package org.crygier.graphql.model.collections;

import groovy.transform.CompileStatic;
import org.crygier.graphql.annotation.SchemaDocumentation;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CollectionTable
import javax.persistence.Column

@Entity
@CompileStatic
public class TypeEntity {

    @Id
    @SchemaDocumentation("Primary Key for the TypeEntity Class")
    String id;

    @Column(name = "NAME")
    String name;
	
    @Column(name = "DESCRIPTION")
    String description;
	
}

