package com.visual.embedding;

import com.visual.config.EmbeddingConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalEmbeddingServiceTest {
    @AfterEach
    void tearDown() {
        clearEmbeddingProperties();
        LocalEmbeddingService.resetForTests();
    }

    @Test
    void staysDisabledWhenNoModelIsConfigured() {
        clearEmbeddingProperties();
        System.setProperty("visual.embedding.modelDir", "C:\\does-not-exist\\xealenium-no-model");
        LocalEmbeddingService service = LocalEmbeddingService.getInstance();

        assertFalse(service.isEnabled());
        assertTrue(service.getDisabledReason().contains("not-configured"));
        assertNull(service.embed("accessible_name=given name"));
    }

    @Test
    void canBeExplicitlyDisabled() {
        System.setProperty("visual.embedding.enabled", "false");
        LocalEmbeddingService service = LocalEmbeddingService.getInstance();

        assertFalse(service.isEnabled());
        assertEquals("disabled-by-property", service.getDisabledReason());
        assertNull(service.embed("accessible_name=email"));
    }

    @Test
    void explicitConfigOverridesSystemPropertyDefaults() {
        System.setProperty("visual.embedding.enabled", "false");
        EmbeddingConfig config = EmbeddingConfig.builder()
            .enabled(true)
            .modelDir(Path.of("C:\\does-not-exist\\xealenium-no-model"))
            .build();

        LocalEmbeddingService service = LocalEmbeddingService.getInstance(config);

        assertFalse(service.isEnabled());
        assertTrue(service.getDisabledReason().contains("not-configured"));
    }

    private static void clearEmbeddingProperties() {
        System.clearProperty("visual.embedding.enabled");
        System.clearProperty("visual.embedding.modelDir");
        System.clearProperty("visual.embedding.modelFile");
        System.clearProperty("visual.embedding.tokenizerFile");
        System.clearProperty("visual.embedding.maxLength");
        System.clearProperty("visual.embedding.modelName");
    }
}
