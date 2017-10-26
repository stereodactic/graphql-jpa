package org.crygier.graphql.model.embeddings;

import groovy.transform.CompileStatic;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
@CompileStatic
public class EmbeddingTest {

    @EmbeddedId
    private EmbeddingId embeddingId;

}
