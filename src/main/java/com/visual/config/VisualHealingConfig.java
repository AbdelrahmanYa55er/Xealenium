package com.visual.config;

import java.util.Objects;

public final class VisualHealingConfig {
    private final Boolean captureBaseline;
    private final boolean refreshBaseline;
    private final boolean interactiveReview;
    private final EmbeddingConfig embeddingConfig;

    private VisualHealingConfig(Builder builder) {
        this.captureBaseline = builder.captureBaseline;
        this.refreshBaseline = builder.refreshBaseline;
        this.interactiveReview = builder.interactiveReview;
        this.embeddingConfig = builder.embeddingConfig == null
            ? EmbeddingConfig.builder().build()
            : builder.embeddingConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static VisualHealingConfig fromSystemProperties() {
        Builder builder = builder();
        String captureBaseline = System.getProperty("visual.captureBaseline", "").trim();
        if (!captureBaseline.isBlank()) {
            builder.captureBaseline(Boolean.parseBoolean(captureBaseline));
        }
        builder.refreshBaseline(Boolean.parseBoolean(System.getProperty("visual.captureBaseline.refresh", "false")));
        builder.interactiveReview(Boolean.parseBoolean(System.getProperty("interactive", "false")));
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
            && Objects.equals(captureBaseline, other.captureBaseline)
            && Objects.equals(embeddingConfig, other.embeddingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(captureBaseline, refreshBaseline, interactiveReview, embeddingConfig);
    }

    public static final class Builder {
        private Boolean captureBaseline;
        private boolean refreshBaseline;
        private boolean interactiveReview;
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

        public Builder embeddingConfig(EmbeddingConfig embeddingConfig) {
            this.embeddingConfig = embeddingConfig;
            return this;
        }

        public VisualHealingConfig build() {
            return new VisualHealingConfig(this);
        }
    }
}
