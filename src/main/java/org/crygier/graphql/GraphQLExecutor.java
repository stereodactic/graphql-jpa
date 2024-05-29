package org.crygier.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.GraphQLEnumType;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

public class GraphQLExecutor {

    @Resource
    private EntityManager entityManager;
    private GraphQL graphQL;

    protected GraphQLExecutor() {}
    public GraphQLExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
        createGraphQL();
    }

    @PostConstruct
    protected void createGraphQL() {
        // Since JPA will deserialize our Enum's for us, we don't want GraphQL doing it.
        // To achieve this, we will delegate calls from GraphQLEnumType's 'serialize' method to
        // our own method (which effectively does nothing).
        ByteBuddyAgent.install();
        new ByteBuddy()
            .redefine(GraphQLEnumType.class)
            .method(ElementMatchers.named("serialize"))
            .intercept(MethodDelegation.to(IdentityCoercing.class))
            .method(ElementMatchers.named("parseLiteral"))
            .intercept(MethodDelegation.to(IdentityCoercing.class))
            .make()
            .load(
                GraphQLEnumType.class.getClassLoader(),
                ClassReloadingStrategy.fromInstalledAgent());

        if (entityManager != null)
            this.graphQL = GraphQL.newGraphQL(new GraphQLSchemaBuilder(entityManager).getGraphQLSchema()).build();
    }

    @Transactional
    public ExecutionResult execute(ExecutionInput executionInput) {
        return graphQL.execute(executionInput);
    }

    @Transactional
    public ExecutionResult execute(String query) {
        return graphQL.execute(query);
    }

    @Transactional
    public ExecutionResult execute(String query, Map<String, Object> arguments) {
        if (arguments == null)
            return graphQL.execute(query);
        return graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables(arguments).build());
    }

}
