package org.crygier.graphql.model.security;

import groovy.transform.CompileStatic;
import org.crygier.graphql.annotation.SchemaDocumentation;
import org.crygier.graphql.annotation.SecurityId

import javax.persistence.Entity
import javax.persistence.FetchType;
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne;

@Entity
@SchemaDocumentation("Representation of a secured object")
@CompileStatic
public class TestOneSecuredChildEntity {

    @Id
    @SchemaDocumentation("Primary Key for the SecuredChildEntity Class")
    Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID", referencedColumnName = "ID")
    private TestOneSecuredParentEntity parent;

    @SchemaDocumentation("Name of the object")
    String name;

    @SecurityId
    @SchemaDocumentation("Security Id")
    Long securityId;
}
