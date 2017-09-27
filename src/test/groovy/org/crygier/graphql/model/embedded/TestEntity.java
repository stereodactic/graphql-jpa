package org.crygier.graphql.model.embedded;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 *
 * @author chris
 */
@Entity
@Table(name = "TEST_ENTITY")
public class TestEntity {
	
	@EmbeddedId
    private TestEntityPK id;

	private String someValue;
	
	public TestEntityPK getId() {
        return this.id;
    }

	public void setId(TestEntityPK id) {
        this.id = id;
    }

	public String getSomeValue() {
		return someValue;
	}

	public void setSomeValue(String someValue) {
		this.someValue = someValue;
	}
	
}
