package com.visual.model;

public final class HealingDecision {
    public final boolean accepted;
    public final CandidateScore candidate;
    public final double bestScore;
    public final int displayIndex;

    private HealingDecision(boolean accepted, CandidateScore candidate, double bestScore, int displayIndex) {
        this.accepted = accepted;
        this.candidate = candidate;
        this.bestScore = bestScore;
        this.displayIndex = displayIndex;
    }

    public static HealingDecision accept(CandidateScore candidate) {
        return new HealingDecision(true, candidate, candidate == null ? 0.0 : candidate.getScore(),
            candidate == null ? -1 : candidate.getOriginalIndex());
    }

    public static HealingDecision abort(double bestScore) {
        return new HealingDecision(false, null, bestScore, -1);
    }
}
