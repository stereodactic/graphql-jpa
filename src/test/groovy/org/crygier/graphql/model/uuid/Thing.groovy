package org.crygier.graphql.model.uuid

import groovy.transform.CompileStatic
import org.crygier.graphql.annotation.SchemaDocumentation

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
@SchemaDocumentation("Database Thing with UUID field")
@CompileStatic
class Thing {

    @Id
    @SchemaDocumentation("Primary Key for the Thing Class")
    UUID id

    String type
}
