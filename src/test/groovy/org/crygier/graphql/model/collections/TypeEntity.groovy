package org.crygier.graphql.model.collections;

import groovy.transform.CompileStatic;
import org.crygier.graphql.annotation.SchemaDocumentation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column

@Entity
@CompileStatic
public class TypeEntity {

    @Id
    @SchemaDocumentation("Primary Key for the TypeEntity Class")
    Integer id;

    @Column(name = "NAME")
    String name;
	
    @Column(name = "DESCRIPTION")
    String description;
	
}

