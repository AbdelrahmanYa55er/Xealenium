package com.visual.model;

public final class FieldProfile {
    private final ElementSnapshot snapshot;
    private final String fieldIdentity;
    private final float[] fieldEmbedding;

    public FieldProfile(ElementSnapshot snapshot, String fieldIdentity, float[] fieldEmbedding) {
        this.snapshot = snapshot;
        this.fieldIdentity = fieldIdentity;
        this.fieldEmbedding = fieldEmbedding;
    }

    public ElementSnapshot getSnapshot() {
        return snapshot;
    }

    public String getFieldIdentity() {
        return fieldIdentity;
    }

    public float[] getFieldEmbedding() {
        return fieldEmbedding;
    }
}
