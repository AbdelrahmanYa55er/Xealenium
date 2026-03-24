package com.visual;

import com.visual.semantic.SemanticSimilarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SemanticSimilarityTest {
    @Test
    void simpleScorePrefersRelatedFieldLabelsOverUnrelatedText() {
        double related = SemanticSimilarity.simpleScore("First Name", "Given Name");
        double unrelated = SemanticSimilarity.simpleScore("First Name", "Email Address");

        assertTrue(related > unrelated, "simple score should favor related field wording");
    }

    @Test
    void simpleScoreRewardsContainedSemanticLabels() {
        double score = SemanticSimilarity.simpleScore("Email", "Primary Email");
        assertTrue(score >= 0.80, "contained labels should score strongly");
    }

    @Test
    void semanticScoreUnderstandsPhoneAndCellWithoutProjectDictionary() {
        double related = SemanticSimilarity.semanticScore("Phone", "Cell");
        double unrelated = SemanticSimilarity.semanticScore("Phone", "Town");

        assertTrue(related > unrelated, "semantic score should favor phone/cell over phone/town");
    }

    @Test
    void semanticScoreUnderstandsCityAndTownWithoutProjectDictionary() {
        double related = SemanticSimilarity.semanticScore("City", "Town");
        double unrelated = SemanticSimilarity.semanticScore("City", "Cell");

        assertTrue(related > unrelated, "semantic score should favor city/town over city/cell");
    }

    @Test
    void semanticScoreHandlesSingleTokenToPhraseSemanticMatches() {
        double related = SemanticSimilarity.semanticScore("Email", "Mail Contact");
        double unrelated = SemanticSimilarity.semanticScore("Email", "Cell");

        assertTrue(related > unrelated, "semantic score should favor email/mail-contact over email/cell");
    }
}
