package com.visual.config;

import java.util.Objects;

public final class VisualHealingConfig {
    private final Boolean captureBaseline;
    private final boolean refreshBaseline;
    private final boolean interactiveReview;
    private final double threshold;
    private final EmbeddingConfig embeddingConfig;

    private VisualHealingConfig(Builder builder) {
        this.captureBaseline = builder.captureBaseline;
        this.refreshBaseline = builder.refreshBaseline;
        this.interactiveReview = builder.interactiveReview;
        this.threshold = builder.threshold;
        this.embeddingConfig = builder.embeddingConfig == null
            ? EmbeddingConfig.builder().build()
            : builder.embeddingConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static VisualHealingConfig fromSystemProperties() {
        Builder builder = builder();
        String captureBaseline = XealeniumRuntimeProperties.get("visual.captureBaseline");
        if (!captureBaseline.isBlank()) {
            builder.captureBaseline(Boolean.parseBoolean(captureBaseline));
        }
        builder.refreshBaseline(XealeniumRuntimeProperties.getBoolean("visual.captureBaseline.refresh", false));
        builder.interactiveReview(XealeniumRuntimeProperties.getBoolean("interactive", false));
        builder.threshold(XealeniumRuntimeProperties.getDouble("visual.threshold", 0.56));
        builder.embeddingConfig(EmbeddingConfig.fromSystemProperties());
        return builder.build();
    }

    public Boolean getCaptureBaseline() {
        return captureBaseline;
    }

    public boolean isRefreshBaseline() {
        return refreshBaseline;
    }

    public boolean isInteractiveReview() {
        return interactiveReview;
    }

    public double getThreshold() {
        return threshold;
    }

    public EmbeddingConfig getEmbeddingConfig() {
        return embeddingConfig;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VisualHealingConfig other)) {
            return false;
        }
        return refreshBaseline == other.refreshBaseline
            && interactiveReview == other.interactiveReview
            && Double.compare(threshold, other.threshold) == 0
            && Objects.equals(captureBaseline, other.captureBaseline)
            && Objects.equals(embeddingConfig, other.embeddingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(captureBaseline, refreshBaseline, interactiveReview, threshold, embeddingConfig);
    }

    public static final class Builder {
        private Boolean captureBaseline;
        private boolean refreshBaseline;
        private boolean interactiveReview;
        private double threshold = 0.56;
        private EmbeddingConfig embeddingConfig;

        public Builder captureBaseline(Boolean captureBaseline) {
            this.captureBaseline = captureBaseline;
            return this;
        }

        public Builder refreshBaseline(boolean refreshBaseline) {
            this.refreshBaseline = refreshBaseline;
            return this;
        }

        public Builder interactiveReview(boolean interactiveReview) {
            this.interactiveReview = interactiveReview;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder embeddingConfig(EmbeddingConfig embeddingConfig) {
            this.embeddingConfig = embeddingConfig;
            return this;
        }

        public VisualHealingConfig build() {
            return new VisualHealingConfig(this);
        }
    }
}
