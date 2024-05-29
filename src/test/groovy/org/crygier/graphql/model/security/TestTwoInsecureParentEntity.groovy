package org.crygier.graphql.model.security

import groovy.transform.CompileStatic;
import org.crygier.graphql.annotation.SchemaDocumentation;

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany;

@Entity
@SchemaDocumentation("Representation of a secured object")
@CompileStatic
class TestTwoInsecureParentEntity {

    @Id
    @SchemaDocumentation("Primary Key for the SecuredParentEntity Class")
    Integer id;

    @SchemaDocumentation("Name of the object")
    String name;

    @OneToMany(mappedBy = "parent")
    private Set<TestTwoSecuredChildEntity> children;
}
