package com.visual.engine;

import com.visual.embedding.EmbeddingFingerprintBuilder;
import com.visual.embedding.LocalEmbeddingService;
import com.visual.model.CandidateMetadata;
import com.visual.model.CollectedElementMetadata;
import com.visual.semantic.SemanticSignalExtractor;
import com.visual.semantic.SemanticSignals;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

final class CandidateMetadataCollector {
    private final CandidateCollector candidateCollector;
    private final SemanticSignalExtractor semanticExtractor;
    private final LocalEmbeddingService embeddingService;

    CandidateMetadataCollector(CandidateCollector candidateCollector, SemanticSignalExtractor semanticExtractor,
                               LocalEmbeddingService embeddingService) {
        this.candidateCollector = candidateCollector;
        this.semanticExtractor = semanticExtractor;
        this.embeddingService = embeddingService;
    }

    List<CandidateMetadata> collect(WebDriver driver, String key, boolean embeddingsActive) {
        List<CollectedElementMetadata> rawCandidates = candidateCollector.collect(driver);
        List<CandidateMetadata> candidates = new ArrayList<>();
        for (int index = 0; index < rawCandidates.size(); index++) {
            CollectedElementMetadata raw = rawCandidates.get(index);
            int x = raw.getX();
            int y = raw.getY();
            int w = raw.getW();
            int h = raw.getH();
            if (w <= 0 || h <= 0) {
                continue;
            }

            WebElement candidateElement = candidateCollector.resolveCandidateElement(driver, index);
            SemanticSignals signals = candidateElement == null
                ? SemanticSignals.empty("unavailable")
                : semanticExtractor.extract(driver, candidateElement);
            String kind = raw.getKind();
            String tagName = raw.getTag();
            String accessibleName = firstNonBlank(signals.getAccessibleName(), raw.getAccessibleName());
            String semanticRole = firstNonBlank(signals.getSemanticRole(), raw.getSemanticRole());
            String autocomplete = firstNonBlank(signals.getAutocomplete(), raw.getAutocomplete());
            String labelText = signals.getLabelText();
            String placeholder = signals.getPlaceholder();
            String descriptionText = signals.getDescriptionText();
            String sectionContext = signals.getSectionContext();
            String parentContext = signals.getParentContext();
            String inputType = signals.getInputType();
            String text = raw.getText();
            String fingerprint = EmbeddingFingerprintBuilder.build(key, kind, tagName, accessibleName, semanticRole, autocomplete,
                labelText, placeholder, descriptionText, sectionContext, parentContext, inputType, text);
            String fieldIdentity = EmbeddingFingerprintBuilder.buildFieldIdentity(
                accessibleName, labelText, placeholder, autocomplete, inputType, text);
            float[] embeddingVector = embeddingsActive ? embeddingService.embed(fingerprint) : null;
            float[] fieldEmbeddingVector = embeddingsActive ? embeddingService.embed(fieldIdentity) : null;
            candidates.add(new CandidateMetadata(index, x, y, w, h, text, raw.getSelector(), kind, tagName,
                accessibleName, semanticRole, autocomplete, labelText, placeholder, descriptionText,
                sectionContext, parentContext, inputType, fingerprint, fieldIdentity, embeddingVector, fieldEmbeddingVector));
        }
        return candidates;
    }

    private static String firstNonBlank(String primary, String fallback) {
        String left = primary == null ? "" : primary.trim();
        String right = fallback == null ? "" : fallback.trim();
        return left.isBlank() ? right : left;
    }
}
