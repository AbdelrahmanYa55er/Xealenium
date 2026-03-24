package com.visual.engine;

import com.visual.model.CandidateScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealingDecisionEngineTest {
    @Test
    void thresholdOnlyStrategyAcceptsBestCandidateAboveThreshold() {
        HealingDecisionEngine engine = new HealingDecisionEngine(0.56);

        assertTrue(engine.decide("demo", null, List.of(), List.of(candidate(0.80)), false).accepted);
    }

    @Test
    void thresholdOnlyStrategyRejectsBestCandidateBelowThreshold() {
        HealingDecisionEngine engine = new HealingDecisionEngine(0.56);

        assertFalse(engine.decide("demo", null, List.of(), List.of(candidate(0.42)), false).accepted);
    }

    @Test
    void customReviewStrategyIsPreservedAcrossDecisionCalls() {
        HealingDecisionEngine engine = new HealingDecisionEngine(0.56);
        engine.setReviewStrategy(new AutoAcceptReviewStrategy());

        assertTrue(engine.decide("demo", null, List.of(), List.of(candidate(0.20)), false).accepted);
        assertTrue(engine.decide("demo", null, List.of(), List.of(candidate(0.20)), true).accepted);
    }

    private CandidateScore candidate(double score) {
        return new CandidateScore(score, 1, "css: #demo", "id", "text", "Demo",
            1, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 50, 50);
    }
}
