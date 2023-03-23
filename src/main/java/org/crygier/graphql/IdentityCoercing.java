package org.crygier.graphql;

import graphql.schema.Coercing;

public class IdentityCoercing {

    public static Object serialize(Object input) {
        return input;
    }

}
