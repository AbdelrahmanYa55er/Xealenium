package com.visual.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class EmbeddingConfig {
    private final boolean enabled;
    private final Path modelDir;
    private final Path modelFile;
    private final Path tokenizerFile;
    private final int maxLength;
    private final String modelName;
    private final String pooling;

    private EmbeddingConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.modelDir = builder.modelDir;
        this.modelFile = builder.modelFile;
        this.tokenizerFile = builder.tokenizerFile;
        this.maxLength = builder.maxLength;
        this.modelName = builder.modelName == null ? "" : builder.modelName.trim();
        this.pooling = builder.pooling == null ? "" : builder.pooling.trim().toLowerCase(Locale.ROOT);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EmbeddingConfig fromSystemProperties() {
        Builder builder = builder();
        String explicit = XealeniumRuntimeProperties.get("visual.embedding.enabled");
        if (!explicit.isBlank()) {
            builder.enabled(Boolean.parseBoolean(explicit));
        }
        String modelDir = XealeniumRuntimeProperties.get("visual.embedding.modelDir");
        if (!modelDir.isBlank()) {
            builder.modelDir(Path.of(modelDir));
        }
        String modelFile = XealeniumRuntimeProperties.get("visual.embedding.modelFile");
        if (modelFile.isBlank()) {
            modelFile = XealeniumRuntimeProperties.get("visual.embedding.modelPath");
        }
        if (!modelFile.isBlank()) {
            builder.modelFile(Path.of(modelFile));
        }
        String tokenizerFile = XealeniumRuntimeProperties.get("visual.embedding.tokenizerFile");
        if (!tokenizerFile.isBlank()) {
            builder.tokenizerFile(Path.of(tokenizerFile));
        }
        String maxLength = XealeniumRuntimeProperties.get("visual.embedding.maxLength");
        if (!maxLength.isBlank()) {
            builder.maxLength(Integer.parseInt(maxLength));
        }
        String modelName = XealeniumRuntimeProperties.get("visual.embedding.modelName");
        if (!modelName.isBlank()) {
            builder.modelName(modelName);
        }
        String pooling = XealeniumRuntimeProperties.get("visual.embedding.pooling");
        if (!pooling.isBlank()) {
            builder.pooling(pooling);
        }
        return builder.build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Path getModelDir() {
        return modelDir;
    }

    public Path getModelFile() {
        return modelFile;
    }

    public Path getTokenizerFile() {
        return tokenizerFile;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPooling() {
        return pooling;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EmbeddingConfig other)) {
            return false;
        }
        return enabled == other.enabled
            && maxLength == other.maxLength
            && Objects.equals(modelDir, other.modelDir)
            && Objects.equals(modelFile, other.modelFile)
            && Objects.equals(tokenizerFile, other.tokenizerFile)
            && Objects.equals(modelName, other.modelName)
            && Objects.equals(pooling, other.pooling);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, modelDir, modelFile, tokenizerFile, maxLength, modelName, pooling);
    }

    public static final class Builder {
        private boolean enabled = true;
        private Path modelDir;
        private Path modelFile;
        private Path tokenizerFile;
        private int maxLength = 128;
        private String modelName = "";
        private String pooling = "";

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder modelDir(Path modelDir) {
            this.modelDir = modelDir;
            return this;
        }

        public Builder modelFile(Path modelFile) {
            this.modelFile = modelFile;
            return this;
        }

        public Builder tokenizerFile(Path tokenizerFile) {
            this.tokenizerFile = tokenizerFile;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder pooling(String pooling) {
            this.pooling = pooling;
            return this;
        }

        public EmbeddingConfig build() {
            return new EmbeddingConfig(this);
        }
    }
}
