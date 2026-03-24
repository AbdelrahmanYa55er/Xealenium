package com.visual.model;

public final class CandidateMetadata {
    public final int originalIndex;
    public final int x;
    public final int y;
    public final int w;
    public final int h;
    public final String text;
    public final String selector;
    public final String kind;
    public final String tagName;
    public final String accessibleName;
    public final String semanticRole;
    public final String autocomplete;
    public final String labelText;
    public final String placeholder;
    public final String descriptionText;
    public final String sectionContext;
    public final String parentContext;
    public final String inputType;
    public final String fingerprint;
    public final String fieldIdentity;
    public final float[] embeddingVector;
    public final float[] fieldEmbeddingVector;
    public int sequence = 1;
    public int kindCount = 1;

    public CandidateMetadata(int originalIndex, int x, int y, int w, int h, String text, String selector, String kind, String tagName,
                             String accessibleName, String semanticRole, String autocomplete, String labelText, String placeholder,
                             String descriptionText, String sectionContext, String parentContext, String inputType,
                             String fingerprint, String fieldIdentity, float[] embeddingVector, float[] fieldEmbeddingVector) {
        this.originalIndex = originalIndex;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.text = text;
        this.selector = selector;
        this.kind = kind;
        this.tagName = tagName;
        this.accessibleName = accessibleName;
        this.semanticRole = semanticRole;
        this.autocomplete = autocomplete;
        this.labelText = labelText;
        this.placeholder = placeholder;
        this.descriptionText = descriptionText;
        this.sectionContext = sectionContext;
        this.parentContext = parentContext;
        this.inputType = inputType;
        this.fingerprint = fingerprint;
        this.fieldIdentity = fieldIdentity;
        this.embeddingVector = embeddingVector;
        this.fieldEmbeddingVector = fieldEmbeddingVector;
    }
}
