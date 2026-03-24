package com.visual.model;

public final class CandidateScore {
    private final double score;
    private final double vis;
    private final double pos;
    private final double txt;
    private final double kindScore;
    private final double seqScore;
    private final double roleScore;
    private final double autocompleteScore;
    private final double semanticScore;
    private final double fieldSemanticScore;
    private final double embeddingScore;
    private final int originalIndex;
    private final int sequence;
    private final int cx;
    private final int cy;
    private final String selector;
    private final String strategy;
    private final String kind;
    private final String accessibleName;

    public CandidateScore(double score, int originalIndex, String selector, String strategy, String kind, String accessibleName,
                          int sequence, double vis, double pos, double txt, double kindScore, double seqScore,
                          double roleScore, double autocompleteScore, double semanticScore, double fieldSemanticScore,
                          double embeddingScore, int cx, int cy) {
        this.score = score;
        this.originalIndex = originalIndex;
        this.selector = selector;
        this.strategy = strategy;
        this.kind = kind;
        this.accessibleName = accessibleName;
        this.sequence = sequence;
        this.vis = vis;
        this.pos = pos;
        this.txt = txt;
        this.kindScore = kindScore;
        this.seqScore = seqScore;
        this.roleScore = roleScore;
        this.autocompleteScore = autocompleteScore;
        this.semanticScore = semanticScore;
        this.fieldSemanticScore = fieldSemanticScore;
        this.embeddingScore = embeddingScore;
        this.cx = cx;
        this.cy = cy;
    }

    public double getScore() { return score; }
    public double getVis() { return vis; }
    public double getPos() { return pos; }
    public double getTxt() { return txt; }
    public double getKindScore() { return kindScore; }
    public double getSeqScore() { return seqScore; }
    public double getRoleScore() { return roleScore; }
    public double getAutocompleteScore() { return autocompleteScore; }
    public double getSemanticScore() { return semanticScore; }
    public double getFieldSemanticScore() { return fieldSemanticScore; }
    public double getEmbeddingScore() { return embeddingScore; }
    public int getOriginalIndex() { return originalIndex; }
    public int getSequence() { return sequence; }
    public int getCx() { return cx; }
    public int getCy() { return cy; }
    public String getSelector() { return selector; }
    public String getStrategy() { return strategy; }
    public String getKind() { return kind; }
    public String getAccessibleName() { return accessibleName; }
}
