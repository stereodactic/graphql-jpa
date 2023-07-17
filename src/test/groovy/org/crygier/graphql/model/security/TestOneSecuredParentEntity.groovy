package org.crygier.graphql.model.security;

import groovy.transform.CompileStatic;
import org.crygier.graphql.annotation.SchemaDocumentation;
import org.crygier.graphql.annotation.SecurityId

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.OneToMany;

@Entity
@SchemaDocumentation("Representation of a secured object")
@CompileStatic
public class TestOneSecuredParentEntity {

    @Id
    @SchemaDocumentation("Primary Key for the SecuredParentEntity Class")
    Integer id;

    @SchemaDocumentation("Name of the object")
    String name;

    @OneToMany(mappedBy = "parent")
    private Set<TestOneSecuredChildEntity> children;

    @SecurityId
    @SchemaDocumentation("Security Id")
    Long securityId;
}