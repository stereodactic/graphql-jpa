package org.crygier.graphql.model.starwars

import groovy.transform.CompileStatic

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity(name = "Human")
@CompileStatic
public class Human extends Character {

    String homePlanet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_droid_id")
    Droid favoriteDroid;

    @ManyToOne
    @JoinColumn(name = "gender_code_id")
    CodeList gender;

	//we intend for this to be eagerly fetched
	@ManyToOne
    @JoinColumn(name = "father_id")
    Human father;
}
