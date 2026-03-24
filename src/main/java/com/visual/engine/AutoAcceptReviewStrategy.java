package com.visual.engine;

import com.visual.model.CandidateScore;
import com.visual.model.HealingDecision;
import com.visual.report.HeatmapRenderer;

import java.awt.image.BufferedImage;
import java.util.List;

final class AutoAcceptReviewStrategy implements HealingReviewStrategy {
    @Override
    public HealingDecision review(String key, BufferedImage pageImg, List<HeatmapRenderer.Candidate> heatCandidates,
                                  List<CandidateScore> rankedCandidates, double threshold) {
        if (rankedCandidates.isEmpty()) {
            return HealingDecision.abort(0.0);
        }
        return HealingDecision.accept(rankedCandidates.get(0));
    }
}
