
package org.crygier.graphql.model.collections;

import groovy.transform.CompileStatic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

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
