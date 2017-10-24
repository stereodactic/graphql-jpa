package org.crygier.graphql.model.collections;

import groovy.transform.CompileStatic;
import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 *
 * @author chris
 */
@Embeddable
@CompileStatic
public class EmbeddedObject {

	@Column(name="COLOR")
	String color;
	
	@Column(name="SHAPE")
	String shape;
	
}
