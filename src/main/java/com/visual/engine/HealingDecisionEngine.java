package com.visual.engine;

import com.visual.model.HealingDecision;
import com.visual.model.CandidateScore;
import com.visual.report.HeatmapRenderer;

import java.awt.image.BufferedImage;
import java.util.List;

final class HealingDecisionEngine {
    private final double threshold;
    private final HealingReviewStrategy thresholdOnlyReviewStrategy;
    private final HealingReviewStrategy swingReviewStrategy;
    private HealingReviewStrategy reviewStrategy;
    private boolean customStrategyActive;

    HealingDecisionEngine(double threshold) {
        this.threshold = threshold;
        this.thresholdOnlyReviewStrategy = new ThresholdOnlyReviewStrategy();
        this.swingReviewStrategy = new SwingReviewStrategy();
        this.reviewStrategy = thresholdOnlyReviewStrategy;
        this.customStrategyActive = false;
    }

    HealingDecision decide(String key, BufferedImage pageImg, List<HeatmapRenderer.Candidate> heatCandidates,
                           List<CandidateScore> rankedCandidates, boolean interactiveMode) {
        if (!customStrategyActive) {
            setInteractiveMode(interactiveMode);
        }
        System.out.println("[REVIEW] Active strategy for " + key + ": " + reviewStrategy.getClass().getSimpleName()
            + " interactive=" + interactiveMode + " custom=" + customStrategyActive + " threshold=" + String.format("%.2f", threshold));
        return reviewStrategy.review(key, pageImg, heatCandidates, rankedCandidates, threshold);
    }

    void setInteractiveMode(boolean interactiveMode) {
        this.reviewStrategy = interactiveMode ? swingReviewStrategy : thresholdOnlyReviewStrategy;
        this.customStrategyActive = false;
        System.out.println("[REVIEW] Interactive mode set to " + interactiveMode + " -> " + reviewStrategy.getClass().getSimpleName());
    }

    void setReviewStrategy(HealingReviewStrategy reviewStrategy) {
        if (reviewStrategy == null) {
            this.reviewStrategy = thresholdOnlyReviewStrategy;
            this.customStrategyActive = false;
            return;
        }
        this.reviewStrategy = reviewStrategy;
        this.customStrategyActive = true;
    }
}
