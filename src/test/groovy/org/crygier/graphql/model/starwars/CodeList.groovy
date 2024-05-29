package org.crygier.graphql.model.starwars

import groovy.transform.CompileStatic
import org.crygier.graphql.annotation.SchemaDocumentation

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
@SchemaDocumentation("Database driven enumeration")
@CompileStatic
class CodeList {

    @Id
    @SchemaDocumentation("Primary Key for the Code List Class")
    Long id;

    String type;
    String code;
    Integer sequence;
    boolean active;
    String description;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    CodeList parent;

}
