package org.crygier.graphql.model.embeddings;

import groovy.transform.CompileStatic;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
@CompileStatic
public class EmbeddingTest {

    @EmbeddedId
    private EmbeddingId embeddingId;

}
