package com.visual;

import com.visual.embedding.EmbeddingFingerprintBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmbeddingFingerprintBuilderTest {
    @Test
    void buildsNormalizedSemanticFingerprint() {
        String fingerprint = EmbeddingFingerprintBuilder.build(
            "By.id: fname",
            "text",
            "input",
            "Given Name",
            "textbox",
            "given-name",
            "Given Name",
            "Type given name",
            "Enter your legal first name",
            "Create Profile",
            "Personal information",
            "text",
            "John"
        );

        assertTrue(fingerprint.contains("locator=id fname"));
        assertTrue(fingerprint.contains("locator_tokens=by id fname"));
        assertTrue(fingerprint.contains("kind=text"));
        assertTrue(fingerprint.contains("tag=input"));
        assertTrue(fingerprint.contains("role=textbox"));
        assertTrue(fingerprint.contains("input_type=text"));
        assertTrue(fingerprint.contains("accessible_name=given name"));
        assertTrue(fingerprint.contains("label_text=given name"));
        assertTrue(fingerprint.contains("placeholder=type given name"));
        assertTrue(fingerprint.contains("description=enter your legal first name"));
        assertTrue(fingerprint.contains("autocomplete=given-name"));
        assertTrue(fingerprint.contains("section_context=create profile"));
        assertTrue(fingerprint.contains("parent_context=personal information"));
        assertTrue(fingerprint.contains("field_identity="));
        assertTrue(fingerprint.contains("given-name"));
        assertTrue(fingerprint.contains("type given name"));
        assertTrue(fingerprint.contains("by id fname"));
        assertTrue(fingerprint.contains("control_identity=text | textbox | input"));
        assertTrue(fingerprint.contains("context_identity=create profile | personal information | enter your legal first name"));
        assertTrue(fingerprint.contains("text=john"));
        assertTrue(fingerprint.contains("semantic_summary="));
    }
}
