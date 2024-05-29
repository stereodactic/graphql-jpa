
package org.crygier.graphql.model.collections;

import groovy.transform.CompileStatic;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

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
	
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TYPE_ENTITY_ID", referencedColumnName = "ID")
    TypeEntity typeEntity
}
