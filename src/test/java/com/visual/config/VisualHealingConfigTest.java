package com.visual.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHealingConfigTest {
    @AfterEach
    void tearDown() {
        System.clearProperty("visual.captureBaseline");
        System.clearProperty("visual.captureBaseline.refresh");
        System.clearProperty("interactive");
        System.clearProperty("visual.threshold");
        System.clearProperty("visual.weight.visual");
        System.clearProperty("visual.weight.position");
        System.clearProperty("visual.weight.text");
        System.clearProperty("visual.weight.kind");
        System.clearProperty("visual.weight.sequence");
        System.clearProperty("visual.weight.role");
        System.clearProperty("visual.weight.autocomplete");
        System.clearProperty("visual.weight.semantic");
        System.clearProperty("visual.weight.field");
        System.clearProperty("visual.weight.embedding");
        System.clearProperty("visual.scoring.maxDistance");
        System.clearProperty("visual.embedding.enabled");
        System.clearProperty("visual.embedding.modelDir");
        System.clearProperty("visual.embedding.modelFile");
        System.clearProperty("visual.embedding.modelPath");
        System.clearProperty("visual.embedding.modelName");
    }

    @Test
    void readsRuntimeDefaultsFromXealeniumPropertiesFile() {
        VisualHealingConfig config = VisualHealingConfig.fromSystemProperties();

        assertEquals(Boolean.FALSE, config.getCaptureBaseline());
        assertFalse(config.isRefreshBaseline());
        assertFalse(config.isInteractiveReview());
        assertEquals(0.70, config.getThreshold());
        assertEquals(0.09, config.getScoringWeights().getVisual());
        assertEquals(0.07, config.getScoringWeights().getPosition());
        assertEquals(0.06, config.getScoringWeights().getText());
        assertEquals(0.10, config.getScoringWeights().getKind());
        assertEquals(0.04, config.getScoringWeights().getSequence());
        assertEquals(0.14, config.getScoringWeights().getRole());
        assertEquals(0.08, config.getScoringWeights().getAutocomplete());
        assertEquals(0.20, config.getScoringWeights().getSemantic());
        assertEquals(0.22, config.getScoringWeights().getField());
        assertEquals(0.12, config.getScoringWeights().getEmbedding());
        assertEquals(600.0, config.getScoringWeights().getMaxDistance());
        assertTrue(config.getEmbeddingConfig().isEnabled());
        assertEquals("gte-small", config.getEmbeddingConfig().getModelName());
        assertEquals(Path.of("models", "gte-small-onnx", "model.onnx"), config.getEmbeddingConfig().getModelFile());
    }

    @Test
    void systemPropertiesOverrideXealeniumPropertiesFileWhenProvided() {
        System.setProperty("visual.captureBaseline", "true");
        System.setProperty("visual.captureBaseline.refresh", "true");
        System.setProperty("interactive", "true");
        System.setProperty("visual.threshold", "0.82");
        System.setProperty("visual.weight.semantic", "0.31");
        System.setProperty("visual.scoring.maxDistance", "720");
        System.setProperty("visual.embedding.enabled", "false");

        VisualHealingConfig config = VisualHealingConfig.fromSystemProperties();

        assertEquals(Boolean.TRUE, config.getCaptureBaseline());
        assertTrue(config.isRefreshBaseline());
        assertTrue(config.isInteractiveReview());
        assertEquals(0.82, config.getThreshold());
        assertEquals(0.31, config.getScoringWeights().getSemantic());
        assertEquals(720.0, config.getScoringWeights().getMaxDistance());
        assertFalse(config.getEmbeddingConfig().isEnabled());
    }

    @Test
    void builderAllowsExplicitApiConfiguration() {
        EmbeddingConfig embeddingConfig = EmbeddingConfig.builder()
            .enabled(true)
            .modelDir(Path.of("models", "gte-small-onnx"))
            .modelName("gte-small")
            .build();

        VisualHealingConfig config = VisualHealingConfig.builder()
            .captureBaseline(null)
            .refreshBaseline(false)
            .interactiveReview(false)
            .scoringWeights(CandidateScoringWeights.builder().semantic(0.30).build())
            .embeddingConfig(embeddingConfig)
            .build();

        assertNull(config.getCaptureBaseline());
        assertFalse(config.isRefreshBaseline());
        assertFalse(config.isInteractiveReview());
        assertEquals(0.30, config.getScoringWeights().getSemantic());
        assertEquals("gte-small", config.getEmbeddingConfig().getModelName());
    }
}
