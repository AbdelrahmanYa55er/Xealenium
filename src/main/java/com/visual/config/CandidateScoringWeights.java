package com.visual.config;

import java.util.Objects;

public final class CandidateScoringWeights {
    public static final double DEFAULT_VISUAL = 0.09;
    public static final double DEFAULT_POSITION = 0.07;
    public static final double DEFAULT_TEXT = 0.06;
    public static final double DEFAULT_KIND = 0.10;
    public static final double DEFAULT_SEQUENCE = 0.04;
    public static final double DEFAULT_ROLE = 0.14;
    public static final double DEFAULT_AUTOCOMPLETE = 0.08;
    public static final double DEFAULT_SEMANTIC = 0.20;
    public static final double DEFAULT_FIELD = 0.22;
    public static final double DEFAULT_EMBEDDING = 0.12;
    public static final double DEFAULT_MAX_DISTANCE = 600.0;

    private final double visual;
    private final double position;
    private final double text;
    private final double kind;
    private final double sequence;
    private final double role;
    private final double autocomplete;
    private final double semantic;
    private final double field;
    private final double embedding;
    private final double maxDistance;

    private CandidateScoringWeights(Builder builder) {
        this.visual = builder.visual;
        this.position = builder.position;
        this.text = builder.text;
        this.kind = builder.kind;
        this.sequence = builder.sequence;
        this.role = builder.role;
        this.autocomplete = builder.autocomplete;
        this.semantic = builder.semantic;
        this.field = builder.field;
        this.embedding = builder.embedding;
        this.maxDistance = builder.maxDistance;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CandidateScoringWeights fromRuntimeProperties() {
        return builder()
            .visual(XealeniumRuntimeProperties.getDouble("visual.weight.visual", DEFAULT_VISUAL))
            .position(XealeniumRuntimeProperties.getDouble("visual.weight.position", DEFAULT_POSITION))
            .text(XealeniumRuntimeProperties.getDouble("visual.weight.text", DEFAULT_TEXT))
            .kind(XealeniumRuntimeProperties.getDouble("visual.weight.kind", DEFAULT_KIND))
            .sequence(XealeniumRuntimeProperties.getDouble("visual.weight.sequence", DEFAULT_SEQUENCE))
            .role(XealeniumRuntimeProperties.getDouble("visual.weight.role", DEFAULT_ROLE))
            .autocomplete(XealeniumRuntimeProperties.getDouble("visual.weight.autocomplete", DEFAULT_AUTOCOMPLETE))
            .semantic(XealeniumRuntimeProperties.getDouble("visual.weight.semantic", DEFAULT_SEMANTIC))
            .field(XealeniumRuntimeProperties.getDouble("visual.weight.field", DEFAULT_FIELD))
            .embedding(XealeniumRuntimeProperties.getDouble("visual.weight.embedding", DEFAULT_EMBEDDING))
            .maxDistance(XealeniumRuntimeProperties.getDouble("visual.scoring.maxDistance", DEFAULT_MAX_DISTANCE))
            .build();
    }

    public double getVisual() {
        return visual;
    }

    public double getPosition() {
        return position;
    }

    public double getText() {
        return text;
    }

    public double getKind() {
        return kind;
    }

    public double getSequence() {
        return sequence;
    }

    public double getRole() {
        return role;
    }

    public double getAutocomplete() {
        return autocomplete;
    }

    public double getSemantic() {
        return semantic;
    }

    public double getField() {
        return field;
    }

    public double getEmbedding() {
        return embedding;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CandidateScoringWeights other)) {
            return false;
        }
        return Double.compare(visual, other.visual) == 0
            && Double.compare(position, other.position) == 0
            && Double.compare(text, other.text) == 0
            && Double.compare(kind, other.kind) == 0
            && Double.compare(sequence, other.sequence) == 0
            && Double.compare(role, other.role) == 0
            && Double.compare(autocomplete, other.autocomplete) == 0
            && Double.compare(semantic, other.semantic) == 0
            && Double.compare(field, other.field) == 0
            && Double.compare(embedding, other.embedding) == 0
            && Double.compare(maxDistance, other.maxDistance) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(visual, position, text, kind, sequence, role, autocomplete, semantic, field, embedding, maxDistance);
    }

    public static final class Builder {
        private double visual = DEFAULT_VISUAL;
        private double position = DEFAULT_POSITION;
        private double text = DEFAULT_TEXT;
        private double kind = DEFAULT_KIND;
        private double sequence = DEFAULT_SEQUENCE;
        private double role = DEFAULT_ROLE;
        private double autocomplete = DEFAULT_AUTOCOMPLETE;
        private double semantic = DEFAULT_SEMANTIC;
        private double field = DEFAULT_FIELD;
        private double embedding = DEFAULT_EMBEDDING;
        private double maxDistance = DEFAULT_MAX_DISTANCE;

        public Builder visual(double visual) {
            this.visual = visual;
            return this;
        }

        public Builder position(double position) {
            this.position = position;
            return this;
        }

        public Builder text(double text) {
            this.text = text;
            return this;
        }

        public Builder kind(double kind) {
            this.kind = kind;
            return this;
        }

        public Builder sequence(double sequence) {
            this.sequence = sequence;
            return this;
        }

        public Builder role(double role) {
            this.role = role;
            return this;
        }

        public Builder autocomplete(double autocomplete) {
            this.autocomplete = autocomplete;
            return this;
        }

        public Builder semantic(double semantic) {
            this.semantic = semantic;
            return this;
        }

        public Builder field(double field) {
            this.field = field;
            return this;
        }

        public Builder embedding(double embedding) {
            this.embedding = embedding;
            return this;
        }

        public Builder maxDistance(double maxDistance) {
            this.maxDistance = maxDistance;
            return this;
        }

        public CandidateScoringWeights build() {
            return new CandidateScoringWeights(this);
        }
    }
}
