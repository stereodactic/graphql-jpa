package org.crygier.graphql;

import graphql.Scalars;
import graphql.schema.*;
import org.crygier.graphql.annotation.GraphQLIgnore;
import org.crygier.graphql.annotation.SchemaDocumentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.metamodel.*;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.Embeddable;
import javax.persistence.criteria.JoinType;

public class GraphQLSchemaBuilder {

    public static final String PAGINATION_REQUEST_PARAM_NAME = "paginationRequest";
    private static final Logger log = LoggerFactory.getLogger(GraphQLSchemaBuilder.class);

    private EntityManager entityManager;

    private Map<Class, GraphQLType> classCache = new HashMap<>();
    private Map<EntityType, GraphQLObjectType> connectorCache = new HashMap<>();
    private Map<EntityType, GraphQLObjectType> entityCache = new HashMap<>();
    private Map<EmbeddableType, GraphQLObjectType> embeddableCache = new HashMap<>();

    public GraphQLSchemaBuilder(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public GraphQLSchema getGraphQLSchema() {
        GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema();
        schemaBuilder.query(getQueryType());

        return schemaBuilder.build();
    }

    GraphQLObjectType getQueryType() {
        GraphQLObjectType.Builder queryType = GraphQLObjectType.newObject().name("QueryType_JPA").description("All encompassing schema for this JPA environment");
        queryType.fields(entityManager.getMetamodel().getEntities().stream().filter(this::isNotIgnored).map(this::getQueryFieldDefinition).collect(Collectors.toList()));
        queryType.fields(entityManager.getMetamodel().getEntities().stream().filter(this::isNotIgnored).map(this::getQueryFieldPageableDefinition).collect(Collectors.toList()));

		//Now that we've generated the base objects and the base objects for pagination, we need to modify the base objects to add nested pagination
		entityManager.getMetamodel().getEntities().stream().filter(this::isNotIgnored).forEach(this::appendNestedPaginationFields);
		
        return queryType.build();
    }

    GraphQLFieldDefinition getQueryFieldDefinition(EntityType<?> entityType) {
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(entityType.getName())
                .description(getSchemaDocumentation(entityType.getJavaType()))
                .type(new GraphQLList(getObjectType(entityType)))
                .dataFetcher(new JpaDataFetcher(entityManager, entityType))
                .argument(entityType.getAttributes().stream().filter(this::isValidInput).filter(this::isNotIgnored).flatMap(this::getArgument).collect(Collectors.toList()))
                .build();
    }

    private GraphQLFieldDefinition getQueryFieldPageableDefinition(EntityType<?> entityType) {
        GraphQLObjectType pageType = getConnectorType(entityType);
		
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(entityType.getName() + "Connection")
                .description("'Connection' request wrapper object for " + entityType.getName() + ".  Use this object in a query to request things like pagination or aggregation in an argument.  Use the 'content' field to request actual fields ")
                .type(pageType)
                .dataFetcher(new ExtendedJpaDataFetcher(entityManager, entityType))
                .argument(paginationArgument)
                .build();
    }

    private GraphQLObjectType getConnectorType(EntityType<?> entityType) {
        if (connectorCache.containsKey(entityType))
            return connectorCache.get(entityType);

		GraphQLObjectType pageType = GraphQLObjectType.newObject()
                .name(entityType.getName() + "Connection")
                .description("'Connection' response wrapper object for " + entityType.getName() + ".  When pagination or aggregation is requested, this object will be returned with metadata about the query.")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("totalPages")
						.description("Total number of pages calculated on the database for this pageSize.").type(Scalars.GraphQLLong).build())
                .field(GraphQLFieldDefinition.newFieldDefinition().name("totalElements")
						.description("Total number of results on the database for this query.").type(Scalars.GraphQLLong).build())
                .field(GraphQLFieldDefinition.newFieldDefinition()
						.name("content")
						.description("The actual object results")
						.type(new GraphQLList(getObjectType(entityType)))
						.dataFetcher(new JpaDataFetcher(entityManager, entityType))
						.argument(entityType.getAttributes().stream().filter(this::isValidInput).filter(this::isNotIgnored).flatMap(this::getArgument).collect(Collectors.toList()))
						.build())
                .build();

		connectorCache.put(entityType, pageType);

        return pageType;
    }

    
    private Stream<GraphQLArgument> getArgument(Attribute attribute) {
		
		if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
			EmbeddableType embeddableType = (EmbeddableType) ((SingularAttribute) attribute).getType();
			Stream<Attribute> s = (Stream<Attribute>) embeddableType.getAttributes().stream();
			return s.filter(a -> a.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED)
					.filter(a -> getAttributeType(a) instanceof GraphQLScalarType).flatMap(this::getArgument);
		} else {
		
			return Arrays.asList(getAttributeType(attribute)).stream()
					.filter(type -> type instanceof GraphQLInputType)
					.filter(type -> attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED ||
							(attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED && type instanceof GraphQLScalarType))
					.map(type -> {
						String name;
						if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
							name = type.getName();
						} else {
							name = attribute.getName();
						}

						/*
						if (!(type instanceof GraphQLList)) {
							type = new GraphQLList(type);
						}
						*/

						return GraphQLArgument.newArgument()
								.name(name)
								//.type((GraphQLInputType) type)
								.type((GraphQLInputType) new GraphQLList(type))
								.build();
					});
		}
    }

    private GraphQLObjectType getObjectType(EntityType<?> entityType) {
        if (entityCache.containsKey(entityType))
            return entityCache.get(entityType);

		//generate standard fields from object
		List<GraphQLFieldDefinition> fieldDefinitions = 
				entityType.getAttributes().stream().filter(this::isNotIgnored).flatMap(this::getObjectField).collect(Collectors.toList());
		
        GraphQLObjectType answer = GraphQLObjectType.newObject()
                .name(entityType.getName())
                .description(getSchemaDocumentation( entityType.getJavaType()))
                .fields(fieldDefinitions)
                .build();

        entityCache.put(entityType, answer);

        return answer;
    }
	
	private GraphQLObjectType getEmbeddableType(Class clazz, EmbeddableType<?> embeddableType) {
		
		if (embeddableCache.containsKey(embeddableType)) {
			return embeddableCache.get(embeddableType);
		}

		List<GraphQLFieldDefinition> fieldDefinitions
				= embeddableType.getAttributes().stream().filter(this::isNotIgnored).flatMap(this::getObjectField).collect(Collectors.toList());

		GraphQLObjectType answer = GraphQLObjectType.newObject()
				.name(clazz.getSimpleName())
				.description(getSchemaDocumentation(embeddableType.getJavaType()))
				.fields(fieldDefinitions)
				.build();

		embeddableCache.put(embeddableType, answer);

		return answer;
	}
	
	private void appendNestedPaginationFields(EntityType<?> entityType) {
		
		GraphQLObjectType entityObject = entityCache.get(entityType);
		
		//the object should always exist in the cache
		if (entityObject != null) {
		
			List<GraphQLFieldDefinition> fieldDefinitions = entityType.getAttributes().stream()
							.filter(this::isNotIgnored).filter(this::isConnectorField).map(this::getObjectConnectorField).collect(Collectors.toList());

			if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
				/* We use reflection to add the fields to the object after it was already built.  This can't be done at build time because of the
				 * potential for circular references
				 */
				try {
					Class clazz = entityObject.getClass();
					Method method = clazz.getDeclaredMethod("buildDefinitionMap", List.class);
					method.setAccessible(true);
					method.invoke(entityObject, fieldDefinitions);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
					log.error("Error appending connector fields to object", ex);
				} catch (NoSuchMethodException | SecurityException ex) {
					log.error("Error appending connector fields to object", ex);
				}
			}
		}
	}
	
    private Stream<GraphQLFieldDefinition> getObjectField(Attribute attribute) {

		if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
			EmbeddableType embeddableType = (EmbeddableType) ((SingularAttribute) attribute).getType();
			Stream<Attribute> s = (Stream<Attribute>) embeddableType.getAttributes().stream();
			return s.flatMap(this::getObjectField);
		} else {

			return Arrays.asList(getAttributeType(attribute)).stream()
					.filter(type -> type instanceof GraphQLOutputType)
					.map(type -> {

						List<GraphQLArgument> arguments = new ArrayList<>();
						arguments.add(GraphQLArgument.newArgument().name("orderBy").type(orderByDirectionEnum).build());            // Always add the orderBy argument
						arguments.add(GraphQLArgument.newArgument().name("joinType").type(joinTypeEnum).build());            // Always add the joinType argument

						// Get the fields that can be queried on (i.e. Simple Types, no Sub-Objects)
						if (attribute instanceof SingularAttribute
								&& attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC
								&& attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED) {

							EntityType foreignType = (EntityType) ((SingularAttribute) attribute).getType();
							Stream<Attribute> attributes = findBasicAttributes(foreignType.getAttributes());

							attributes.forEach(it -> {
								arguments.addAll(getArgument(it).collect(Collectors.toList()));
							});

							//To do this, the id of the parent would have to be taken into account here so that it actually queries based upon the parent
							//relationship.  This still retains the n+1 problem though
							return GraphQLFieldDefinition.newFieldDefinition()
									.name(attribute.getName())
									.description(getSchemaDocumentation(attribute.getJavaMember()))
									.type((GraphQLOutputType) type)
									.dataFetcher(new JpaDataFetcher(entityManager, foreignType))
									.argument(arguments)
									.build();

						} else if (attribute instanceof PluralAttribute) {

							//TODO: this is hacky, attempting to prevent "java.lang.ClassCastException: org.hibernate.jpa.internal.metamodel.BasicTypeImpl cannot be cast to javax.persistence.metamodel.EntityType"
							if (((PluralAttribute) attribute).getElementType() instanceof EntityType) {
								EntityType foreignType = (EntityType) ((PluralAttribute) attribute).getElementType();
								Stream<Attribute> attributes = findBasicAttributes(foreignType.getAttributes());

								attributes.forEach(it -> {
									arguments.addAll(getArgument(it).collect(Collectors.toList()));
								});

								//To do this, the id of the parent would have to be taken into account here so that it actually queries based upon the parent
								//relationship.  This still retains the n+1 problem though
								return GraphQLFieldDefinition.newFieldDefinition()
										.name(attribute.getName())
										.description(getSchemaDocumentation(attribute.getJavaMember()))
										.type((GraphQLOutputType) type)
										.dataFetcher(new JpaDataFetcher(entityManager, foreignType))
										.argument(arguments)
										.build();

							}
							/* else if (((PluralAttribute) attribute).getElementType() instanceof EmbeddableType) {
					EmbeddableType foreignType = (EmbeddableType) ((PluralAttribute) attribute).getElementType();
					Stream<Attribute> attributes = findBasicAttributes(foreignType.getAttributes());

					attributes.forEach(it -> {
						arguments.addAll(getArgument(it).collect(Collectors.toList()));
					});

					//To do this, the id of the parent would have to be taken into account here so that it actually queries based upon the parent
					//relationship.  This still retains the n+1 problem though
					return GraphQLFieldDefinition.newFieldDefinition()
							.name(attribute.getName())
							.description(getSchemaDocumentation(attribute.getJavaMember()))
							.type((GraphQLOutputType) type)
							.dataFetcher(new JpaDataFetcher(entityManager, foreignType))
							.argument(arguments)
							.build();
				} */

						}

						/*
						String name;
						if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED && !(type instanceof GraphQLScalarType)) {
							name = type.getName();
						} else {
							name = attribute.getName();
						}
						*/

						return GraphQLFieldDefinition.newFieldDefinition()
								.name(attribute.getName())
								.description(getSchemaDocumentation(attribute.getJavaMember()))
								.type((GraphQLOutputType) type)
								.argument(arguments)
								.build();

					});
		}
	}

    private boolean isConnectorField(Attribute attribute) {
		
        boolean result = false;
		
		if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED) {
			GraphQLType type = getAttributeType(attribute);
		
			if (type instanceof GraphQLOutputType) {	
				if (attribute instanceof SingularAttribute 
						&& attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC
						&& attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED) {
					result = true;
				} else if (attribute instanceof PluralAttribute) {
					//TODO: this is hacky, attempting to prevent "java.lang.ClassCastException: org.hibernate.jpa.internal.metamodel.BasicTypeImpl cannot be cast to javax.persistence.metamodel.EntityType"
					if (((PluralAttribute) attribute).getElementType() instanceof EntityType) {
						result = true;
					}
				}
			}
		}
		
		return result;
    }

	private GraphQLFieldDefinition getObjectConnectorField(Attribute attribute) {
        //GraphQLType type = getAttributeType(attribute);
			
		if (attribute instanceof SingularAttribute 
				&& attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC
				&& attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED) {
			
			EntityType entityType = (EntityType) ((SingularAttribute) attribute).getType();

			return GraphQLFieldDefinition.newFieldDefinition()
				.name(attribute.getName() + "Connection")
				.description("'Connection' request wrapper object for " + entityType.getName() + ".  Use this object in a query to request things like pagination or aggregation in an argument.  Use the 'content' field to request actual fields ")
				.type(getConnectorType(entityType))
				.dataFetcher(new ExtendedJpaDataFetcher(entityManager, entityType))
				.argument(paginationArgument)
				.build();

		} else if (attribute instanceof PluralAttribute) {

			//TODO: this is hacky, attempting to prevent "java.lang.ClassCastException: org.hibernate.jpa.internal.metamodel.BasicTypeImpl cannot be cast to javax.persistence.metamodel.EntityType"
			if (((PluralAttribute) attribute).getElementType() instanceof EntityType) {
				EntityType entityType = (EntityType) ((PluralAttribute) attribute).getElementType();

				return GraphQLFieldDefinition.newFieldDefinition()
				.name(attribute.getName() + "Connection")
				.description("'Connection' request wrapper object for " + entityType.getName() + ".  Use this object in a query to request things like pagination or aggregation in an argument.  Use the 'content' field to request actual fields ")
				.type(getConnectorType(entityType))
				.dataFetcher(new ExtendedJpaDataFetcher(entityManager, entityType))
				.argument(paginationArgument)
				.build();
			}
		}
		
		throw new IllegalArgumentException("Attribute " + attribute + " cannot be mapped as an Connector Field");
	}
	
    private Stream<Attribute> findBasicAttributes(Collection<Attribute> attributes) {
        return attributes.stream().filter(this::isNotIgnored).filter(it -> it.getPersistentAttributeType() == Attribute.PersistentAttributeType.BASIC);
    }

    private GraphQLType getBasicAttributeType(Class javaType) {
        if (String.class.isAssignableFrom(javaType))
            return Scalars.GraphQLString;
        else if (UUID.class.isAssignableFrom(javaType))
            return JavaScalars.GraphQLUUID;
        else if (Integer.class.isAssignableFrom(javaType) || int.class.isAssignableFrom(javaType))
            return Scalars.GraphQLInt;
        else if (Short.class.isAssignableFrom(javaType) || short.class.isAssignableFrom(javaType))
            return Scalars.GraphQLShort;
        else if (Float.class.isAssignableFrom(javaType) || float.class.isAssignableFrom(javaType)
                || Double.class.isAssignableFrom(javaType) || double.class.isAssignableFrom(javaType))
            return Scalars.GraphQLFloat;
        else if (Long.class.isAssignableFrom(javaType) || long.class.isAssignableFrom(javaType))
            return Scalars.GraphQLLong;
        else if (Boolean.class.isAssignableFrom(javaType) || boolean.class.isAssignableFrom(javaType))
            return Scalars.GraphQLBoolean;
        else if (Date.class.isAssignableFrom(javaType))
            return JavaScalars.GraphQLDate;
        else if (LocalDateTime.class.isAssignableFrom(javaType))
            return JavaScalars.GraphQLLocalDateTime;
        else if (LocalDate.class.isAssignableFrom(javaType))
            return JavaScalars.GraphQLLocalDate;
        else if (javaType.isEnum()) {
            return getTypeFromJavaType(javaType);
        } else if (BigDecimal.class.isAssignableFrom(javaType)) {
            return Scalars.GraphQLBigDecimal;
        }

        throw new UnsupportedOperationException(
                "Class could not be mapped to GraphQL: '" + javaType.getTypeName() + "'");
    }

    private GraphQLType getAttributeType(Attribute attribute) {
        if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.BASIC) {
            try {
                return getBasicAttributeType(attribute.getJavaType());
            } catch (UnsupportedOperationException e) {
                //fall through to the exception below
                //which is more useful because it also contains the declaring member
            }
        } else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_MANY || attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_MANY) {
            EntityType foreignType = (EntityType) ((PluralAttribute) attribute).getElementType();
            return new GraphQLList(new GraphQLTypeReference(foreignType.getName()));
        } else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE || attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
            EntityType foreignType = (EntityType) ((SingularAttribute) attribute).getType();
            return new GraphQLTypeReference(foreignType.getName());
        } else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ELEMENT_COLLECTION) {
            Type foreignType = ((PluralAttribute) attribute).getElementType();
            return new GraphQLList(getTypeFromJavaType(foreignType.getJavaType()));
        }  /* else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
            EmbeddableType embeddableType = (EmbeddableType) ((SingularAttribute) attribute).getType();
            Stream<Attribute> s = (Stream<Attribute>) embeddableType.getAttributes().stream();
            return s.flatMap(this::getAttributeType);
        } */

        final String declaringType = attribute.getDeclaringType().getJavaType().getName(); // fully qualified name of the entity class
        final String declaringMember = attribute.getJavaMember().getName(); // field name in the entity class

        throw new UnsupportedOperationException(
                "Attribute could not be mapped to GraphQL: field '" + declaringMember + "' of entity class '" + declaringType + "'");
    }

    private boolean isValidInput(Attribute attribute) {
        return attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.BASIC ||
                attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ELEMENT_COLLECTION ||
                attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED;
    }

    private String getSchemaDocumentation(Member member) {
        if (member instanceof AnnotatedElement) {
            return getSchemaDocumentation((AnnotatedElement) member);
        }

        return null;
    }

    private String getSchemaDocumentation(AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            SchemaDocumentation schemaDocumentation = annotatedElement.getAnnotation(SchemaDocumentation.class);
            return schemaDocumentation != null ? schemaDocumentation.value() : null;
        }

        return null;
    }

    private boolean isNotIgnored(Attribute attribute) {
        return isNotIgnored(attribute.getJavaMember()) && isNotIgnored(attribute.getJavaType());
    }

    private boolean isNotIgnored(EntityType entityType) {
        return isNotIgnored(entityType.getJavaType());
    }

    private boolean isNotIgnored(Member member) {
        return member instanceof AnnotatedElement && isNotIgnored((AnnotatedElement) member);
    }

    private boolean isNotIgnored(AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            GraphQLIgnore schemaDocumentation = annotatedElement.getAnnotation(GraphQLIgnore.class);
            return schemaDocumentation == null;
        }

        return false;
    }

    private GraphQLType getTypeFromJavaType(Class clazz) {
		
        if (clazz.isEnum()) {
			if (classCache.containsKey(clazz)) {
				return classCache.get(clazz);
			}

			GraphQLEnumType.Builder enumBuilder = GraphQLEnumType.newEnum().name(clazz.getSimpleName());
			int ordinal = 0;
			for (Enum enumValue : ((Class<Enum>) clazz).getEnumConstants()) {
				enumBuilder.value(enumValue.name(), ordinal++);
			}

			GraphQLType answer = enumBuilder.build();
			setIdentityCoercing(answer);

			classCache.put(clazz, answer);

			return answer;
		} else if (clazz.getAnnotation(Embeddable.class) != null) {
			EmbeddableType<?> embeddableType = entityManager.getMetamodel().embeddable(clazz);
			return getEmbeddableType(clazz, embeddableType);
		}

        return getBasicAttributeType(clazz);
    }

    /**
     * A bit of a hack, since JPA will deserialize our Enum's for us...we don't want GraphQL doing it.
     *
     * @param type
     */
    private void setIdentityCoercing(GraphQLType type) {
        try {
            Field coercing = type.getClass().getDeclaredField("coercing");
            coercing.setAccessible(true);
            coercing.set(type, new IdentityCoercing());
        } catch (Exception e) {
            log.error("Unable to set coercing for " + type, e);
        }
    }

    private static final GraphQLArgument paginationArgument =
            GraphQLArgument.newArgument()
                    .name(PAGINATION_REQUEST_PARAM_NAME)
                    .type(GraphQLInputObjectType.newInputObject()
                            .name("PaginationObject")
                            .description("Query object for Pagination Requests, specifying the requested page, and that page's size.\n\nNOTE: 'page' parameter is 1-indexed, NOT 0-indexed.\n\nExample: paginationRequest { page: 1, size: 20 }")
                            .field(GraphQLInputObjectField.newInputObjectField().name("page").description("Which page should be returned, starting with 1 (1-indexed)").type(Scalars.GraphQLInt).build())
                            .field(GraphQLInputObjectField.newInputObjectField().name("size").description("How many results should this page contain").type(Scalars.GraphQLInt).build())
                            .build()
                    ).build();

    private static final GraphQLEnumType orderByDirectionEnum =
            GraphQLEnumType.newEnum()
                    .name("OrderByDirection")
                    .description("Describes the direction (Ascending / Descending) to sort a field.")
                    .value("ASC", 0, "Ascending")
                    .value("DESC", 1, "Descending")
                    .build();

	private static final GraphQLEnumType joinTypeEnum =
            GraphQLEnumType.newEnum()
                    .name("joinType")
                    .description("Describes the join type for relating fields")
                    .value(JoinType.INNER.name(), JoinType.INNER.ordinal(), JoinType.INNER.toString())
                    .value(JoinType.LEFT.name(), JoinType.LEFT.ordinal(), JoinType.LEFT.toString())
                    .value(JoinType.RIGHT.name(), JoinType.RIGHT.ordinal(), JoinType.RIGHT.toString())
                    .build();
}
