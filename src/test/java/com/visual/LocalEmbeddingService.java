package com.visual;

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

public class LocalEmbeddingService {
    private static final String LOG_PREFIX = "[EMBEDDING]";
    private static volatile LocalEmbeddingService instance;

    private final boolean enabled;
    private final String modelName;
    private final String disabledReason;
    private final OnnxTextEmbeddingModel model;

    public static LocalEmbeddingService getInstance() {
        LocalEmbeddingService current = instance;
        if (current != null) {
            return current;
        }
        synchronized (LocalEmbeddingService.class) {
            if (instance == null) {
                instance = new LocalEmbeddingService();
            }
            return instance;
        }
    }

    static void resetForTests() {
        synchronized (LocalEmbeddingService.class) {
            if (instance != null && instance.model != null) {
                instance.model.closeQuietly();
            }
            instance = null;
        }
    }

    private LocalEmbeddingService() {
        String explicit = System.getProperty("visual.embedding.enabled", "").trim();
        boolean resolvedEnabled;
        String resolvedModelName;
        String resolvedDisabledReason;
        OnnxTextEmbeddingModel resolvedModel;
        if ("false".equalsIgnoreCase(explicit)) {
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
                    int maxLength = Integer.parseInt(System.getProperty("visual.embedding.maxLength", "128"));
                    String configuredName = System.getProperty("visual.embedding.modelName", "").trim();
                    resolvedModelName = configuredName.isBlank()
                        ? modelFile.getParent().getFileName().toString()
                        : configuredName;
                    String pooling = resolvePoolingStrategy(resolvedModelName, modelFile);
                    resolvedModel = new OnnxTextEmbeddingModel(resolvedModelName, modelFile, tokenizerFile, Math.max(16, maxLength), pooling);
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

    private static Path resolveModelFile() {
        String explicitFile = System.getProperty("visual.embedding.modelFile", "").trim();
        if (!explicitFile.isBlank()) {
            Path path = Path.of(explicitFile);
            return Files.exists(path) ? path : null;
        }
        String modelDir = System.getProperty("visual.embedding.modelDir", "").trim();
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

    private static Path resolveTokenizerFile(Path modelFile) {
        String explicitFile = System.getProperty("visual.embedding.tokenizerFile", "").trim();
        if (!explicitFile.isBlank()) {
            Path path = Path.of(explicitFile);
            return Files.exists(path) ? path : null;
        }
        if (modelFile == null || modelFile.getParent() == null) {
            return null;
        }
        Path tokenizer = modelFile.getParent().resolve("tokenizer.json");
        return Files.exists(tokenizer) ? tokenizer : null;
    }

    private static String resolvePoolingStrategy(String modelName, Path modelFile) {
        String explicit = System.getProperty("visual.embedding.pooling", "").trim().toLowerCase(Locale.ROOT);
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
