package org.crygier.graphql.model.security

import groovy.transform.CompileStatic;
import org.crygier.graphql.annotation.SchemaDocumentation;
import org.crygier.graphql.annotation.SecurityId

import jakarta.persistence.Entity
import jakarta.persistence.FetchType;
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne;

@Entity
@SchemaDocumentation("Representation of a secured object")
@CompileStatic
class TestTwoSecuredChildEntity {

    @Id
    @SchemaDocumentation("Primary Key for the SecuredChildEntity Class")
    Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID", referencedColumnName = "ID")
    private TestTwoInsecureParentEntity parent;

    @SchemaDocumentation("Name of the object")
    String name;

    @SecurityId
    @SchemaDocumentation("Security Id")
    Long securityId;
}
