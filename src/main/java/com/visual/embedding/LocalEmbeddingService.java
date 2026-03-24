package com.visual.embedding;

import com.visual.config.EmbeddingConfig;
import com.visual.model.ElementSnapshot;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LocalEmbeddingService {
    private static final String LOG_PREFIX = "[EMBEDDING]";
    private static volatile LocalEmbeddingService instance;
    private static final Map<EmbeddingConfig, LocalEmbeddingService> INSTANCES = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final String modelName;
    private final String disabledReason;
    private final OnnxTextEmbeddingModel model;
    private final EmbeddingConfig config;

    public static LocalEmbeddingService getInstance() {
        LocalEmbeddingService current = instance;
        if (current != null) {
            return current;
        }
        synchronized (LocalEmbeddingService.class) {
            if (instance == null) {
                instance = new LocalEmbeddingService(EmbeddingConfig.fromSystemProperties());
            }
            return instance;
        }
    }

    public static LocalEmbeddingService getInstance(EmbeddingConfig config) {
        EmbeddingConfig resolved = config == null ? EmbeddingConfig.fromSystemProperties() : config;
        if (resolved.equals(EmbeddingConfig.fromSystemProperties())) {
            return getInstance();
        }
        return INSTANCES.computeIfAbsent(resolved, LocalEmbeddingService::new);
    }

    static void resetForTests() {
        synchronized (LocalEmbeddingService.class) {
            if (instance != null && instance.model != null) {
                instance.model.closeQuietly();
            }
            instance = null;
        }
        INSTANCES.values().forEach(service -> {
            if (service.model != null) {
                service.model.closeQuietly();
            }
        });
        INSTANCES.clear();
    }

    private LocalEmbeddingService(EmbeddingConfig config) {
        this.config = config == null ? EmbeddingConfig.fromSystemProperties() : config;
        boolean resolvedEnabled;
        String resolvedModelName;
        String resolvedDisabledReason;
        OnnxTextEmbeddingModel resolvedModel;
        if (!this.config.isEnabled()) {
            resolvedEnabled = false;
            resolvedModelName = "";
            resolvedDisabledReason = "disabled-by-property";
            resolvedModel = null;
        } else {
            Path modelFile = resolveModelFile();
            Path tokenizerFile = resolveTokenizerFile(modelFile);
            if (modelFile == null || tokenizerFile == null) {
                resolvedEnabled = false;
                resolvedModelName = "";
                resolvedDisabledReason = "model-or-tokenizer-not-configured";
                resolvedModel = null;
            } else {
                try {
                    int maxLength = Math.max(16, this.config.getMaxLength());
                    String configuredName = this.config.getModelName();
                    resolvedModelName = configuredName.isBlank()
                        ? modelFile.getParent().getFileName().toString()
                        : configuredName;
                    String pooling = resolvePoolingStrategy(resolvedModelName, modelFile);
                    resolvedModel = new OnnxTextEmbeddingModel(resolvedModelName, modelFile, tokenizerFile, maxLength, pooling);
                    resolvedEnabled = true;
                    resolvedDisabledReason = "";
                    System.out.println(LOG_PREFIX + " Loaded local model '" + resolvedModelName + "' from " + modelFile + " pooling=" + pooling);
                } catch (Exception e) {
                    resolvedEnabled = false;
                    resolvedModelName = "";
                    resolvedDisabledReason = e.getMessage();
                    resolvedModel = null;
                    System.out.println(LOG_PREFIX + " Disabled local embeddings: " + resolvedDisabledReason);
                }
            }
        }
        enabled = resolvedEnabled;
        modelName = resolvedModelName;
        disabledReason = resolvedDisabledReason;
        model = resolvedModel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

    public EmbeddingConfig getConfig() {
        return config;
    }

    public float[] embed(String fingerprint) {
        if (!enabled || fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        try {
            return model.embed(fingerprint);
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + " Embedding failed: " + e.getMessage());
            return null;
        }
    }

    public float[] embeddingForSnapshot(String locator, ElementSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot.embeddingVector != null && snapshot.embeddingVector.length > 0
            && modelName.equals(snapshot.embeddingModel)) {
            return snapshot.embeddingVector;
        }
        String fingerprint = snapshot.semanticFingerprint;
        if (fingerprint == null || fingerprint.isBlank()) {
            fingerprint = EmbeddingFingerprintBuilder.forSnapshot(locator, snapshot);
        }
        return embed(fingerprint);
    }

    public static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private Path resolveModelFile() {
        Path configuredFile = config.getModelFile();
        if (configuredFile != null) {
            Path path = configuredFile.toAbsolutePath();
            return Files.exists(path) ? path : null;
        }
        Path configuredDir = config.getModelDir();
        String modelDir = configuredDir == null ? "" : configuredDir.toString().trim();
        if (modelDir.isBlank()) {
            Path defaultDir = Path.of("models", "gte-small-onnx").toAbsolutePath();
            if (Files.isDirectory(defaultDir)) {
                modelDir = defaultDir.toString();
            }
        }
        if (modelDir.isBlank()) {
            return null;
        }
        Path dir = Path.of(modelDir);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path defaultModel = dir.resolve("model.onnx");
        if (Files.exists(defaultModel)) {
            return defaultModel;
        }
        try {
            return Files.list(dir)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".onnx"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private Path resolveTokenizerFile(Path modelFile) {
        Path configuredFile = config.getTokenizerFile();
        if (configuredFile != null) {
            Path path = configuredFile.toAbsolutePath();
            return Files.exists(path) ? path : null;
        }
        if (modelFile == null || modelFile.getParent() == null) {
            return null;
        }
        Path tokenizer = modelFile.getParent().resolve("tokenizer.json");
        return Files.exists(tokenizer) ? tokenizer : null;
    }

    private String resolvePoolingStrategy(String modelName, Path modelFile) {
        String explicit = config.getPooling();
        if (!explicit.isBlank()) {
            return explicit;
        }
        String hint = (modelName + " " + (modelFile == null ? "" : modelFile.toString())).toLowerCase(Locale.ROOT);
        if (hint.contains("bge")) {
            return "cls";
        }
        return "mean";
    }

    private static final class OnnxTextEmbeddingModel {
        private final OrtEnvironment environment;
        private final OrtSession session;
        private final HuggingFaceTokenizer tokenizer;
        private final int maxLength;
        private final String outputName;
        private final boolean expectsTokenTypeIds;
        private final String poolingStrategy;

        private OnnxTextEmbeddingModel(String modelName, Path modelFile, Path tokenizerFile, int maxLength, String poolingStrategy) throws OrtException, IOException {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(modelFile.toString(), new OrtSession.SessionOptions());
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile);
            this.maxLength = maxLength;
            this.outputName = session.getOutputNames().iterator().next();
            Set<String> inputNames = session.getInputNames();
            this.expectsTokenTypeIds = inputNames.contains("token_type_ids");
            this.poolingStrategy = poolingStrategy == null || poolingStrategy.isBlank() ? "mean" : poolingStrategy;
        }

        private float[] embed(String fingerprint) throws OrtException {
            Encoding encoding = tokenizer.encode(fingerprint);
            long[] inputIds = truncate(encoding.getIds());
            long[] attentionMask = truncate(encoding.getAttentionMask());
            long[] typeIds = truncate(encoding.getTypeIds());

            Map<String, OnnxTensor> inputs = new HashMap<>();
            try (OnnxTensor idsTensor = OnnxTensor.createTensor(environment, new long[][]{inputIds});
                 OnnxTensor maskTensor = OnnxTensor.createTensor(environment, new long[][]{attentionMask})) {
                inputs.put("input_ids", idsTensor);
                inputs.put("attention_mask", maskTensor);

                OnnxTensor typeTensor = null;
                if (expectsTokenTypeIds) {
                    typeTensor = OnnxTensor.createTensor(environment, new long[][]{typeIds});
                    inputs.put("token_type_ids", typeTensor);
                }

                try (OrtSession.Result result = session.run(inputs)) {
                    float[] vector = extractEmbedding(result.get(outputName).get(), attentionMask);
                    return normalize(vector);
                } finally {
                    if (typeTensor != null) {
                        typeTensor.close();
                    }
                }
            }
        }

        private long[] truncate(long[] values) {
            int length = Math.min(values.length, maxLength);
            long[] out = new long[length];
            System.arraycopy(values, 0, out, 0, length);
            return out;
        }

        private float[] extractEmbedding(Object outputValue, long[] attentionMask) {
            if (outputValue instanceof OnnxTensor tensor) {
                try {
                    return extractEmbedding(tensor.getValue(), attentionMask);
                } catch (OrtException e) {
                    throw new IllegalStateException("Unable to read ONNX tensor output", e);
                }
            }
            if (outputValue instanceof float[][][] tensor3) {
                if ("cls".equals(poolingStrategy)) {
                    return tensor3[0][0];
                }
                return meanPool(tensor3[0], attentionMask);
            }
            if (outputValue instanceof float[][] tensor2) {
                return tensor2[0];
            }
            if (outputValue instanceof float[] tensor1) {
                return tensor1;
            }
            if (outputValue instanceof Object[] objects && objects.length > 0 && objects[0] instanceof float[][] nested2) {
                return nested2[0];
            }
            throw new IllegalStateException("Unsupported embedding output type: " + outputValue.getClass().getName());
        }

        private float[] meanPool(float[][] tokens, long[] attentionMask) {
            int length = tokens.length;
            int width = tokens[0].length;
            float[] pooled = new float[width];
            int count = 0;
            for (int i = 0; i < length; i++) {
                boolean attend = attentionMask == null || i >= attentionMask.length || attentionMask[i] != 0L;
                if (!attend) {
                    continue;
                }
                count++;
                for (int j = 0; j < width; j++) {
                    pooled[j] += tokens[i][j];
                }
            }
            if (count == 0) {
                return pooled;
            }
            for (int j = 0; j < width; j++) {
                pooled[j] /= count;
            }
            return pooled;
        }

        private float[] normalize(float[] vector) {
            double norm = 0.0;
            for (float value : vector) {
                norm += value * value;
            }
            if (norm == 0.0) {
                return vector;
            }
            float scale = (float) (1.0 / Math.sqrt(norm));
            float[] normalized = new float[vector.length];
            for (int i = 0; i < vector.length; i++) {
                normalized[i] = vector[i] * scale;
            }
            return normalized;
        }

        private void closeQuietly() {
            try {
                session.close();
            } catch (Exception ignored) {
            }
            try {
                environment.close();
            } catch (Exception ignored) {
            }
        }
    }
}


