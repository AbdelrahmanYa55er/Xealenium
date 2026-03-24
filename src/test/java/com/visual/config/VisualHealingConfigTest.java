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
        System.clearProperty("visual.embedding.enabled");
        System.clearProperty("visual.embedding.modelDir");
        System.clearProperty("visual.embedding.modelName");
    }

    @Test
    void readsRuntimeDefaultsFromSystemProperties() {
        System.setProperty("visual.captureBaseline", "true");
        System.setProperty("visual.captureBaseline.refresh", "true");
        System.setProperty("interactive", "true");
        System.setProperty("visual.embedding.enabled", "false");

        VisualHealingConfig config = VisualHealingConfig.fromSystemProperties();

        assertEquals(Boolean.TRUE, config.getCaptureBaseline());
        assertTrue(config.isRefreshBaseline());
        assertTrue(config.isInteractiveReview());
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
            .embeddingConfig(embeddingConfig)
            .build();

        assertNull(config.getCaptureBaseline());
        assertFalse(config.isRefreshBaseline());
        assertFalse(config.isInteractiveReview());
        assertEquals("gte-small", config.getEmbeddingConfig().getModelName());
    }
}
