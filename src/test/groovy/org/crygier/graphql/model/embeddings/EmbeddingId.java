package org.crygier.graphql.model.embeddings;

import org.crygier.graphql.model.starwars.Character;
import org.crygier.graphql.model.starwars.Episode;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.io.Serializable;

@Embeddable
class EmbeddingId implements Serializable {

    @ManyToOne
    @JoinColumn(name = "character_id")
    private Character character;

    @Column(name = "episode")
    private Episode episode;

    @Column(name = "age")
    private int age;
	
	@Column(name = "weight")
	private int weight;

    public EmbeddingId(Character character, Episode episode, int age, int weight) {
        this.character = character;
        this.episode = episode;
        this.age = age;
		this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EmbeddingId that = (EmbeddingId) o;

        if (age != that.age) return false;
		if (weight != that.weight) return false;
        if (character != null ? !character.equals(that.character) : that.character != null) return false;
        return episode == that.episode;
    }

    @Override
    public int hashCode() {
        int result = character != null ? character.hashCode() : 0;
        result = 31 * result + (episode != null ? episode.hashCode() : 0);
        result = 31 * result + age;
        result = 31 * result + weight;
        return result;
    }
}
