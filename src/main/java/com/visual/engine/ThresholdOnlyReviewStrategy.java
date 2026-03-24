package com.visual.engine;

import com.visual.model.CandidateScore;
import com.visual.model.HealingDecision;
import com.visual.report.HeatmapRenderer;

import java.awt.image.BufferedImage;
import java.util.List;

final class ThresholdOnlyReviewStrategy implements HealingReviewStrategy {
    @Override
    public HealingDecision review(String key, BufferedImage pageImg, List<HeatmapRenderer.Candidate> heatCandidates,
                                  List<CandidateScore> rankedCandidates, double threshold) {
        if (rankedCandidates.isEmpty() || rankedCandidates.get(0).getScore() < threshold) {
            return HealingDecision.abort(rankedCandidates.isEmpty() ? 0.0 : rankedCandidates.get(0).getScore());
        }
        return HealingDecision.accept(rankedCandidates.get(0));
    }
}
