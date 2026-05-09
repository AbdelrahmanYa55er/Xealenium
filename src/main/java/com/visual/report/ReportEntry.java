package com.visual.report;

import com.visual.model.CandidateScore;

public class ReportEntry {
    public final String originalLocator;
    public final String newLocator;
    public final double score;
    public final String heatmapFilename;
    public final boolean accepted;
    public final String selectorStrategy;
    public final String candidateKind;
    public final String accessibleName;
    public final String pageRegion;
    public final int candidateSequence;
    public final double visualScore;
    public final double positionScore;
    public final double textScore;
    public final double kindScore;
    public final double sequenceScore;
    public final double roleScore;
    public final double autocompleteScore;
    public final double semanticScore;
    public final double fieldScore;
    public final double embeddingScore;

    public ReportEntry(String originalLocator, String newLocator, double score, String heatmapFilename) {
        this(originalLocator, newLocator, score, heatmapFilename, false, "", "", "", "", -1,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public ReportEntry(String originalLocator, CandidateScore candidate, String heatmapFilename) {
        this(originalLocator, candidate.getSelector(), candidate.getScore(), heatmapFilename, true,
            candidate.getStrategy(), candidate.getKind(), candidate.getAccessibleName(), candidate.getPageRegion(), candidate.getSequence(),
            candidate.getVis(), candidate.getPos(), candidate.getTxt(), candidate.getKindScore(),
            candidate.getSeqScore(), candidate.getRoleScore(), candidate.getAutocompleteScore(),
            candidate.getSemanticScore(), candidate.getFieldSemanticScore(), candidate.getEmbeddingScore());
    }

    private ReportEntry(String originalLocator, String newLocator, double score, String heatmapFilename,
                        boolean accepted, String selectorStrategy, String candidateKind, String accessibleName,
                        String pageRegion, int candidateSequence, double visualScore, double positionScore, double textScore,
                        double kindScore, double sequenceScore, double roleScore, double autocompleteScore,
                        double semanticScore, double fieldScore, double embeddingScore) {
        this.originalLocator = originalLocator;
        this.newLocator = newLocator;
        this.score = score;
        this.heatmapFilename = heatmapFilename;
        this.accepted = accepted;
        this.selectorStrategy = selectorStrategy;
        this.candidateKind = candidateKind;
        this.accessibleName = accessibleName;
        this.pageRegion = pageRegion;
        this.candidateSequence = candidateSequence;
        this.visualScore = visualScore;
        this.positionScore = positionScore;
        this.textScore = textScore;
        this.kindScore = kindScore;
        this.sequenceScore = sequenceScore;
        this.roleScore = roleScore;
        this.autocompleteScore = autocompleteScore;
        this.semanticScore = semanticScore;
        this.fieldScore = fieldScore;
        this.embeddingScore = embeddingScore;
    }
}
