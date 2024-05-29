package org.crygier.graphql


import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@Configuration
@ContextConfiguration(loader = SpringBootContextLoader, classes = TestApplication)
class EmbeddedSchemaBuildTest extends Specification {

    @Autowired
    private EntityManager entityManager;

    private GraphQLSchemaBuilder builder;

    void setup() {
        builder = new GraphQLSchemaBuilder(entityManager);
    }

    def 'Correctly flattens embedded keys into distinct fields'() {
        when:
        def embeddingEntity = entityManager.getMetamodel().getEntities().stream().filter { e -> e.name == "EmbeddingTest"}.findFirst().get()
        def graphQlObject = builder.getObjectType(embeddingEntity)

        then:
        graphQlObject.fieldDefinitions.size() == 4
    }

    def 'Correctly extract embedded basic query fields'() {
        when:
        def embeddingEntity = entityManager.getMetamodel().getEntities().stream().filter { e -> e.name == "EmbeddingTest"}.findFirst().get()
        def graphQlFieldDefinition = builder.getQueryFieldDefinition(embeddingEntity)

        then:
		//right now the expectation is that embedded scalars will be applied as arguments
        graphQlFieldDefinition.arguments.size() == 2
    }

    def 'Correctly extract a whole moddel with embeddings'() {
        when:
        def q = builder.getQueryType()

        then:
        true
    }

}
