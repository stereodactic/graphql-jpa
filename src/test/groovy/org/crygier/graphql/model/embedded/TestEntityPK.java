package org.crygier.graphql.model.embedded;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import org.springframework.beans.factory.annotation.Configurable;

/**
 *
 * @author chris
 */
@Configurable
@Embeddable
public class TestEntityPK implements Serializable {
	
	@Column(name = "FIRST_PART", nullable = false)
	private Long firstPart;
	
	@Column(name = "SECOND_PART", nullable = false)
	private Long secondPart;
	
	public TestEntityPK(Long firstPart, Long secondPart) {
        super();
        this.firstPart = firstPart;
		this.secondPart = secondPart;
    }

	private TestEntityPK() {
        super();
    }

	public Long getFirstPart() {
		return firstPart;
	}

	public void setFirstPart(Long firstPart) {
		this.firstPart = firstPart;
	}

	public Long getSecondPart() {
		return secondPart;
	}

	public void setSecondPart(Long secondPart) {
		this.secondPart = secondPart;
	}
}
