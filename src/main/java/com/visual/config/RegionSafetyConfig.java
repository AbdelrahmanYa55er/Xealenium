package com.visual.config;

import java.util.Objects;

public final class RegionSafetyConfig {
    public static final boolean DEFAULT_ENABLED = true;
    public static final double DEFAULT_CROSS_REGION_PENALTY = 0.55;
    public static final double DEFAULT_MIN_CONTEXT_OVERLAP = 0.15;
    public static final double DEFAULT_MAX_FAR_POSITION_SCORE = 0.05;

    private final boolean enabled;
    private final double crossRegionPenalty;
    private final double minContextOverlap;
    private final double maxFarPositionScore;

    private RegionSafetyConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.crossRegionPenalty = builder.crossRegionPenalty;
        this.minContextOverlap = builder.minContextOverlap;
        this.maxFarPositionScore = builder.maxFarPositionScore;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RegionSafetyConfig fromRuntimeProperties() {
        return builder()
            .enabled(XealeniumRuntimeProperties.getBoolean("visual.safety.region.enabled", DEFAULT_ENABLED))
            .crossRegionPenalty(XealeniumRuntimeProperties.getDouble("visual.safety.crossRegionPenalty", DEFAULT_CROSS_REGION_PENALTY))
            .minContextOverlap(XealeniumRuntimeProperties.getDouble("visual.safety.minContextOverlap", DEFAULT_MIN_CONTEXT_OVERLAP))
            .maxFarPositionScore(XealeniumRuntimeProperties.getDouble("visual.safety.maxFarPositionScore", DEFAULT_MAX_FAR_POSITION_SCORE))
            .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getCrossRegionPenalty() {
        return crossRegionPenalty;
    }

    public double getMinContextOverlap() {
        return minContextOverlap;
    }

    public double getMaxFarPositionScore() {
        return maxFarPositionScore;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RegionSafetyConfig other)) {
            return false;
        }
        return enabled == other.enabled
            && Double.compare(crossRegionPenalty, other.crossRegionPenalty) == 0
            && Double.compare(minContextOverlap, other.minContextOverlap) == 0
            && Double.compare(maxFarPositionScore, other.maxFarPositionScore) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, crossRegionPenalty, minContextOverlap, maxFarPositionScore);
    }

    public static final class Builder {
        private boolean enabled = DEFAULT_ENABLED;
        private double crossRegionPenalty = DEFAULT_CROSS_REGION_PENALTY;
        private double minContextOverlap = DEFAULT_MIN_CONTEXT_OVERLAP;
        private double maxFarPositionScore = DEFAULT_MAX_FAR_POSITION_SCORE;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder crossRegionPenalty(double crossRegionPenalty) {
            this.crossRegionPenalty = crossRegionPenalty;
            return this;
        }

        public Builder minContextOverlap(double minContextOverlap) {
            this.minContextOverlap = minContextOverlap;
            return this;
        }

        public Builder maxFarPositionScore(double maxFarPositionScore) {
            this.maxFarPositionScore = maxFarPositionScore;
            return this;
        }

        public RegionSafetyConfig build() {
            return new RegionSafetyConfig(this);
        }
    }
}
