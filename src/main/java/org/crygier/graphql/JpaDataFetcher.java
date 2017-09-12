package org.crygier.graphql;

import graphql.language.*;
import graphql.schema.*;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import java.util.*;
import java.util.stream.Collectors;

public class JpaDataFetcher implements DataFetcher {

    protected EntityManager entityManager;
    protected EntityType<?> entityType;

    public JpaDataFetcher(EntityManager entityManager, EntityType<?> entityType) {
        this.entityManager = entityManager;
        this.entityType = entityType;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        return getQuery(environment, environment.getFields().iterator().next()).getResultList();
    }

    protected TypedQuery getQuery(DataFetchingEnvironment environment, Field field) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery((Class) entityType.getJavaType());
        Root root = query.from(entityType);

		List<Predicate> predicates = new ArrayList<>();
		
		getQueryHelper(environment, field, cb, query, root, root, predicates);

		//arguments to the top-level field
		//TODO: this may needs to pass root.fetch for top-level relationships
		predicates.addAll(field.getArguments().stream().map(it -> getPredicate(cb, 
				root.get(it.getName()).getModel() instanceof SingularAttribute ? root.get(it.getName()): root.join(it.getName()), 
				environment, it)).collect(Collectors.toList()));

        query.where(predicates.toArray(new Predicate[predicates.size()]));
		
        return entityManager.createQuery(query.distinct(true));
    }
	
	protected void getQueryHelper(DataFetchingEnvironment environment, Field field, 
			CriteriaBuilder cb, CriteriaQuery<Object> query, From from, Path path, List<Predicate> predicates) {
		
		// Loop through all of the fields being requested
        field.getSelectionSet().getSelections().forEach(selection -> {
            if (selection instanceof Field) {
                Field selectedField = (Field) selection;

                // "__typename" is part of the graphql introspection spec and has to be ignored by jpa
                if(!"__typename".equals(selectedField.getName())) {

                    Path fieldPath = path.get(selectedField.getName());

                    // Process the orderBy clause
					//TODO: this isn't going to handle nested orderBy fields properly
                    Optional<Argument> orderByArgument = selectedField.getArguments().stream().filter(it -> "orderBy".equals(it.getName())).findFirst();
                    if (orderByArgument.isPresent()) {
                        if ("DESC".equals(((EnumValue) orderByArgument.get().getValue()).getName()))
                            query.orderBy(cb.desc(fieldPath));
                        else
                            query.orderBy(cb.asc(fieldPath));
                    }

					Join join = null;
					
                    // Check if it's an object and the foreign side is One.  Then we can eagerly fetch causing an inner join instead of 2 queries
                    if (fieldPath.getModel() instanceof SingularAttribute) {
                        SingularAttribute attribute = (SingularAttribute) fieldPath.getModel();
                        if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE || attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                            //TODO: we can eagerly fetch TO_ONE associations assuming that the parent was also eagerly fetched
							//Fetch fetch = from.fetch(selectedField.getName());
							join = from.join(selectedField.getName());
						}
						
                    } else { //Otherwise, assume the foreign side is many
						if (selectedField.getSelectionSet() != null) {
							//Fetch fetch = from.fetch(selectedField.getName());
							join = from.join(selectedField.getName());
						}
					}
					
					if (join != null) {
						final Join forLambda = (Join) join;
						
						getQueryHelper(environment, selectedField, cb, query, ((From)forLambda), ((Join) forLambda), predicates);
							
						predicates.addAll(selectedField.getArguments().stream()
							.filter(it -> !"orderBy".equals(it.getName()))
							.map(it -> new Argument(it.getName(), it.getValue()))
							.collect(Collectors.toList())
							.stream().map(it -> getPredicate(cb, ((Join) forLambda).get(it.getName()), environment, it)).collect(Collectors.toList()));
					}
                }
            }
        });
		
	}

	private Predicate getPredicate(CriteriaBuilder cb, Path path, DataFetchingEnvironment environment, Argument argument) {
            
			// If the argument is a list, let's assume we need to join and do an 'in' clause
			if (path.getModel() instanceof SingularAttribute) {
				return cb.equal(path, convertValue(environment, argument, argument.getValue()));
			} else {
				return path.in(convertValue(environment, argument, argument.getValue()));
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
            } else {
                // Return provided StringValue
                return ((StringValue) value).getValue();
            }
        }
        else if (value instanceof VariableReference)
            return environment.getArguments().get(((VariableReference) value).getName());
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
}
