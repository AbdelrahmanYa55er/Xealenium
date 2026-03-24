package com.visual.engine;

import com.visual.model.CandidateScore;
import com.visual.report.HeatmapRenderer;

import java.util.List;

final class CandidateScoringResult {
    final List<CandidateScore> rankedCandidates;
    final List<HeatmapRenderer.Candidate> heatmapCandidates;
    final int totalCandidates;

    CandidateScoringResult(List<CandidateScore> rankedCandidates, List<HeatmapRenderer.Candidate> heatmapCandidates, int totalCandidates) {
        this.rankedCandidates = rankedCandidates;
        this.heatmapCandidates = heatmapCandidates;
        this.totalCandidates = totalCandidates;
    }
}
