package org.crygier.graphql;

import graphql.language.*;
import graphql.schema.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import java.util.*;
import java.util.stream.Collectors;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JpaDataFetcher implements DataFetcher {

    protected EntityManager entityManager;
    protected EntityType<?> entityType;
	
    private static final Logger log = LoggerFactory.getLogger(JpaDataFetcher.class);

    public JpaDataFetcher(EntityManager entityManager, EntityType<?> entityType) {
        this.entityManager = entityManager;
        this.entityType = entityType;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
		
		Object result = null;
		Field field = environment.getFields().iterator().next();
		
		TypedQuery typedQuery = getQuery(environment, field);
		
		if (environment.getSource() instanceof PaginationResult) {
			PaginationResult paginationResult = (PaginationResult) environment.getSource();
			typedQuery.setMaxResults(paginationResult.getPageSize()).setFirstResult((paginationResult.getPage() - 1) * paginationResult.getPageSize());
		}
		
		if (environment.getSource() != null && !(environment.getSource() instanceof PaginationResult)) {
			List resultList = typedQuery.getResultList();

			Member member = null;
			
			if (environment.getSource().getClass().getAnnotation(Entity.class) != null) {
				member = entityManager.getMetamodel().entity(environment.getSource().getClass()).getAttribute(field.getName()).getJavaMember();
			} else if (environment.getSource().getClass().getAnnotation(Embeddable.class) != null) {
				member = entityManager.getMetamodel().embeddable(environment.getSource().getClass()).getAttribute(field.getName()).getJavaMember();
			}
			
			java.lang.reflect.Field property = (java.lang.reflect.Field) member;

			if (Collection.class.isAssignableFrom(property.getType())) {
				result = resultList;
			} else {
				if (resultList.size() == 1) {
					result = resultList.get(0);
				} else {
					log.warn("Potentially unexpected number of results returned: " + resultList.size());
				}
			}
			
		} else {
			result = typedQuery.getResultList();
		}
		
        return result;
    }

    protected TypedQuery getQuery(DataFetchingEnvironment environment, Field field) {
		
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery((Class) entityType.getJavaType());
		
		buildCriteriaQuery(environment, field, cb, query, true);
		//query.distinct(true);
		
        return entityManager.createQuery(query);
    }

    protected Root buildCriteriaQuery(DataFetchingEnvironment environment, Field field, CriteriaBuilder cb, CriteriaQuery<Object> query, boolean isFullQuery) {
        
		Root root = query.from(entityType);
		
		if (isFullQuery) {
			//process the order by for the root before we process any children in getQueryHelper
			processOrderBy(field, root, query, cb);
		}

		//recurse through the child fields
		getQueryHelper(environment, field, cb, query, root, root, isFullQuery);

		List<Predicate> predicates = new ArrayList<>();

		//arguments to the top-level field
		predicates.addAll(field.getArguments().stream()
				.filter(it -> (!"orderBy".equals(it.getName()) && !"joinType".equals(it.getName())))
				.map(it -> getPredicate(cb, getRootArgumentPath(root, it), environment, it))
				.collect(Collectors.toList()));

		//if there is a source, this is a nested query, we need to apply the filtering from the parent
		//we check to ensure that this isn't a count query because the parent is not an entity in that case
		if (environment.getSource() != null) {
			Predicate predicate = getPredicateForParent(environment, cb, root, query);
		
			if (predicate != null) {
				predicates.add(predicate);
			}
		}
		
        query.where(predicates.toArray(new Predicate[predicates.size()]));
		
		return root;
    }
	
	protected void getQueryHelper(DataFetchingEnvironment environment, Field field, 
			CriteriaBuilder cb, CriteriaQuery<Object> query, From from, Path path, boolean parentFetched) {
		
		//the selectionSet may be null when dealing with count queries
		if (field.getSelectionSet() != null) {
			// Loop through all of the fields being requested
			field.getSelectionSet().getSelections().forEach(selection -> {
				if (selection instanceof Field) {
					Field selectedField = (Field) selection;

					
					if (selectedField.getName().contains("Connection")) {
						
						Optional<graphql.language.Selection> content = 
								field.getSelectionSet().getSelections().stream().filter(it -> ("content".equals(((Field)it).getName()))).findFirst();
						
						if (content.isPresent()) {
							getQueryHelper(environment, (Field) content.get(), cb, query, from, path, parentFetched);
						}
						
					} else if (!"__typename".equals(selectedField.getName())) {
						// "__typename" is part of the graphql introspection spec and has to be ignored by jpa
						
						Path fieldPath = path.get(selectedField.getName());

						//make left joins the default
						JoinType joinType = JoinType.LEFT;

						Optional<Argument> joinTypeArgument = selectedField.getArguments().stream().filter(it -> "joinType".equals(it.getName())).findFirst();
						if (joinTypeArgument.isPresent()) {
							joinType = JoinType.valueOf(((EnumValue) joinTypeArgument.get().getValue()).getName());
						}

						List<Argument> arguments = selectedField.getArguments().stream()
									.filter(it -> (!"orderBy".equals(it.getName()) && !"joinType".equals(it.getName())))
									.map(it -> new Argument(it.getName(), it.getValue()))
									.collect(Collectors.toList());

						boolean fetched = false;
						Join join = null;

						// Check if it's an object and the foreign side is One.  Then we can eagerly fetch causing an inner join instead of 2 queries
						if (fieldPath.getModel() instanceof SingularAttribute) {
							SingularAttribute attribute = (SingularAttribute) fieldPath.getModel();
							if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE || attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
								//we can eagerly fetch TO_ONE associations assuming that the parent was also eagerly fetched
								//hibernate doesn't allow fetches with 'with-clauses' so if there are arguments, we can't fetch
								
								//disabled fetching due to issues with sub-queries and count queries
								/* if (parentFetched && arguments.size() == 0) {
									join = (Join) from.fetch(selectedField.getName(), joinType);
									fetched = true;
								} else { */
									join = from.join(selectedField.getName(), joinType);
								//}
							}

						} else { //Otherwise, assume the foreign side is many
							if (selectedField.getSelectionSet() != null) {
								//Fetch fetch = from.fetch(selectedField.getName(), joinType);
								//TODO: if we fetch, update the boolean
								join = from.join(selectedField.getName(), joinType);
							}
						}

						//Let's assume that we can eventually figure out when to fetch (taking nesting into account) and when not to
						if (fetched) {
							//it's safe to process the ordering for this instances children
							processOrderBy(selectedField, join, query, cb);
						}

						if (join != null) {
							/* If we have a join, assume we need to distinct the query
							 * TODO: Realistically, we should only need to do this then the join is a ToMany join, because 
							 * that will be responsible for expanding the result set.
							 */
							query.distinct(true);
							
							final Join forLambda = (Join) join;

							getQueryHelper(environment, selectedField, cb, query, ((From)forLambda), ((Join) forLambda), fetched);

							List<Predicate> joinPredicates = arguments.stream().map(
									it -> getPredicate(cb, ((Join) forLambda).get(it.getName()), environment, it)).collect(Collectors.toList()
								);

							// don't blow away an existing condition
							if (forLambda.getOn() != null) {
								joinPredicates.add(forLambda.getOn());
							}

							//add the predicates to the on to faciliate outer joins
							forLambda.on(joinPredicates.toArray(EMPTY_PREDICATES));
						}
					}
				}
			});
		}
		
	}

	private Predicate getPredicate(CriteriaBuilder cb, Path path, DataFetchingEnvironment environment, Argument argument) {
            
			//this must be an object in case an enum is returned
			Object value = convertValue(environment, argument, argument.getValue());
			
			if (value instanceof Collection) {
				return path.in((Collection)value);
			} else {
				return cb.equal(path, value);
			}
    }

    protected Object convertValue(DataFetchingEnvironment environment, Argument argument, Value value) {
        if (value instanceof StringValue) {
			Object convertedValue = null;
			//TODO: The UUID behavior causes issues if you attempt to parse further down the hierarchy
			if (environment.getArguments() != null && environment.getArguments().containsKey(argument.getName())) {
				 convertedValue = environment.getArgument(argument.getName()); //This only works when dealing with the top level object
			}
		   
            if (convertedValue != null && convertedValue instanceof UUID) { 
                // Return real parameter for instance UUID even if the Value is a StringValue
                return convertedValue;
            } else if (convertedValue != null && convertedValue instanceof List) {
				List convertedValueList = (List) convertedValue;
				
				//TODO: this won't properly handle queries on multiple UUIDs
				if (!convertedValueList.isEmpty()) {
					convertedValue = convertedValueList.get(0);
					if (convertedValue instanceof UUID) {
						return convertedValue;
					}
				}
			} 
			
			// Return provided StringValue
			return ((StringValue) value).getValue();
        }
        else if (value instanceof VariableReference)
			//the VariableReference value is the name of the variable - the variable value is not passed as an argument to the environment
			//instead the graphql-java library does the substitution, so we can just ask for the desired argument
            //return environment.getArguments().get(((VariableReference) value).getName());
			return environment.getArguments().get(argument.getName());
        else if (value instanceof ArrayValue)
            return ((ArrayValue) value).getValues().stream().map((it) -> convertValue(environment, argument, it)).collect(Collectors.toList());
        else if (value instanceof EnumValue) {
			//TODO: why does this rely on the environment - This likely doesn't work for nested values
            Class enumType = getJavaType(environment, argument);
            return Enum.valueOf(enumType, ((EnumValue) value).getName());
        } else if (value instanceof IntValue) {
            return ((IntValue) value).getValue();
        } else if (value instanceof BooleanValue) {
            return ((BooleanValue) value).isValue();
        } else if (value instanceof FloatValue) {
            return ((FloatValue) value).getValue();
        }

        return value.toString();
    }

    private Class getJavaType(DataFetchingEnvironment environment, Argument argument) {
        Attribute argumentEntityAttribute = getAttribute(environment, argument);

        if (argumentEntityAttribute instanceof PluralAttribute)
            return ((PluralAttribute) argumentEntityAttribute).getElementType().getJavaType();

        return argumentEntityAttribute.getJavaType();
    }

    private Attribute getAttribute(DataFetchingEnvironment environment, Argument argument) {
        GraphQLObjectType objectType = getObjectType(environment, argument);
        EntityType entityType = getEntityType(objectType);

        return entityType.getAttribute(argument.getName());
    }

    private EntityType getEntityType(GraphQLObjectType objectType) {
        return entityManager.getMetamodel().getEntities().stream().filter(it -> it.getName().equals(objectType.getName())).findFirst().get();
    }

    private GraphQLObjectType getObjectType(DataFetchingEnvironment environment, Argument argument) {
        GraphQLType outputType = environment.getFieldType();
        if (outputType instanceof GraphQLList)
            outputType = ((GraphQLList) outputType).getWrappedType();

        if (outputType instanceof GraphQLObjectType)
            return (GraphQLObjectType) outputType;

        return null;
    }

	private Predicate getPredicateForParent(DataFetchingEnvironment environment, CriteriaBuilder cb, Root root, CriteriaQuery query) {
		
		Predicate result = null;
		Object parent = null;
		String fieldName = null;
		
		if (environment.getSource() instanceof PaginationResult) {
			PaginationResult paginationResult = ((PaginationResult) environment.getSource());
			parent = paginationResult.getParent();
			if (parent != null) {
				fieldName = paginationResult.getFieldName().substring(0, paginationResult.getFieldName().indexOf("Connection"));
			}
		} else {
			parent = environment.getSource();
			fieldName = environment.getFields().get(0).getName();
			if (fieldName.contains("Connection")) {
				fieldName = fieldName.substring(0, fieldName.indexOf("Connection"));
			}
		}
		
		if (fieldName != null) {
			//get the source, this will be used to filter the query
			
			Member member = null;
			
			if (parent.getClass().getAnnotation(Entity.class) != null) {
				member = entityManager.getMetamodel().entity(parent.getClass()).getAttribute(fieldName).getJavaMember();
			} else if (parent.getClass().getAnnotation(Embeddable.class) != null) {
				member = entityManager.getMetamodel().embeddable(parent.getClass()).getAttribute(fieldName).getJavaMember();
			}

			//TODO: this might need criteria for method as javaField.getAnnotation(OneToMany.class);
			if (member instanceof java.lang.reflect.Field) {
				java.lang.reflect.Field javaField = (java.lang.reflect.Field) member;

				OneToOne oneToOne = javaField.getAnnotation(OneToOne.class);
				OneToMany oneToMany = javaField.getAnnotation(OneToMany.class);
				ManyToOne manyToOne = javaField.getAnnotation(ManyToOne.class);
				ManyToMany manyToMany = javaField.getAnnotation(ManyToMany.class);

				if (oneToOne != null && oneToOne.mappedBy() != null && !"".equals(oneToOne.mappedBy().trim())) { //TODO: use StringUtils instead?
					result = cb.equal(root.get(oneToOne.mappedBy()), cb.literal(parent));
				} else if (oneToMany != null) { 
					String mappedBy = oneToMany.mappedBy();
					if (mappedBy != null && !"".equals(mappedBy.trim())) { //TODO: use StringUtils instead?
						result = cb.equal(root.get(mappedBy), cb.literal(parent));
					} else {
						Subquery subQuery = generateSubQuery(query, root, parent, cb, fieldName);
						result = root.in(subQuery);
					}
				} else if (manyToMany != null) {

					Subquery subQuery = generateSubQuery(query, root, parent, cb, fieldName);
					result = root.in(subQuery);
				} else if ( manyToOne != null || oneToOne != null) {
					result = getFieldValueThroughHibernateProxy(parent, fieldName, cb, root);
					
					if (result == null) {
						result = getFieldValueAsLiteral(parent, fieldName, cb, root);
					}
				}
			}
		}
			
		return result;
	}

	private Subquery generateSubQuery(CriteriaQuery query, Root root, Object parent, CriteriaBuilder cb, String fieldName) {
		//the OneToOne case is also intended here
		
		/* Since the @ManyToMany only needs to be defined one side we can't assume that this side has a clean mapping
		* back to the parent.  The tests provide a good example of this (C-3PO is not one of Han's friends, even though
		* C-3PO considers Han a friend.
		*
		* This link provides guidance: https://stackoverflow.com/questions/4483576/jpa-2-0-criteria-api-subqueries-in-expressions
		*/
		Subquery subQuery = query.subquery(root.getJavaType());
		Root subQueryRoot = subQuery.from(parent.getClass());
		subQuery.where(cb.equal(subQueryRoot, cb.literal(parent)));
		Join subQueryJoin = subQueryRoot.join(fieldName);
		subQuery.select(subQueryJoin);
		return subQuery;
	}

	private Predicate getFieldValueThroughHibernateProxy(Object parent, String fieldName, CriteriaBuilder cb, Root root) {

		Predicate result = null;
		
		//This implementation may be fragile if hibernate changes it's implementation.  However, the performance benefits
		//of using the id is much better than using a subquery
		Class clazz = parent.getClass();
		try {
			java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
			field.setAccessible(true);

			Object fieldValue = field.get(parent);

			if (fieldValue != null) {
				java.lang.reflect.Field handlerField = fieldValue.getClass().getDeclaredField("handler");
				handlerField.setAccessible(true);

				Object handler = handlerField.get(fieldValue);

				try {
					if (Class.forName("org.hibernate.proxy.LazyInitializer").isAssignableFrom(handler.getClass())) {
						Method getIdentifier = handler.getClass().getMethod("getIdentifier", (Class[]) null);
						result = cb.equal(root, getIdentifier.invoke(handler, (Object[]) null));
					}
				} catch (NoSuchMethodException | InvocationTargetException ex) {
					log.warn("Error attempting to process hibernate proxy");
					log.debug("Error invoking method", ex);
				} catch (ClassNotFoundException ex) {
					//This just means that we're not using hibernate
					log.warn("Unable process hibernate proxy - Hibernate does not appear to be present");
				}
			} else {
				result = cb.isNull(root);
			}

		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
				log.warn("Error attempting to process hibernate proxy");
				log.debug("Error accessing field", ex);
		}
		
		return result;
	}
	
	private Predicate getFieldValueAsLiteral(Object parent, String fieldName, CriteriaBuilder cb, Root root) {
		
		Predicate result = null;
		
		Class clazz = parent.getClass();
		try {
			java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
			field.setAccessible(true);

			Object fieldValue = field.get(parent);

			result = cb.equal(root, cb.literal(fieldValue));
			
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
				log.warn("Error attempting to get field value");
				log.debug("Error accessing field", ex);
		}
		
		return result;
	}

	private void processOrderBy(Field field, Path path, CriteriaQuery<Object> query, CriteriaBuilder cb) {
		
		//the selectionSet may be null when dealing with count queries
		if (field.getSelectionSet() != null) {
			// Loop through this fields selections and apply the ordering
			field.getSelectionSet().getSelections().forEach(selection -> {
				if (selection instanceof Field) {
					Field selectedField = (Field) selection;

					// "__typename" is part of the graphql introspection spec and has to be ignored by jpa
					if(!"__typename".equals(selectedField.getName()) && !selectedField.getName().contains("Connection")) {

						Path fieldPath = path.get(selectedField.getName());

						// Process the orderBy clause - orderBy can only be processed for fields actually being returned, but this should be smart enough to account for fetches
						Optional<Argument> orderByArgument = selectedField.getArguments().stream().filter(it -> "orderBy".equals(it.getName())).findFirst();
						if (orderByArgument.isPresent()) {

							List<Order> orders = new ArrayList<>();

							//add the previous ordering first so that it is retained
							if (query.getOrderList() != null) {
								orders.addAll(query.getOrderList());
							}

							if ("DESC".equals(((EnumValue) orderByArgument.get().getValue()).getName())) {
								orders.add(cb.desc(fieldPath));
							} else {
								orders.add(cb.asc(fieldPath));
							}

							query.orderBy(orders);
						}
					}
				}
			});
		}
	}

	private Path getRootArgumentPath(Root root, Argument it) {
		//This only needs to the join case when we're querying by an ENUM, otherwise the arguments will be "primitive" values
		return root.get(it.getName()).getModel() instanceof SingularAttribute ? root.get(it.getName()): root.join(it.getName(), JoinType.LEFT);
	}
	
	private static final Predicate[] EMPTY_PREDICATES = {};
}