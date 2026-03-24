package com.visual.engine;

import com.visual.baseline.BaselineStore;
import com.visual.baseline.PageIdentityService;
import com.visual.embedding.EmbeddingFingerprintBuilder;
import com.visual.embedding.LocalEmbeddingService;
import com.visual.image.ImageUtils;
import com.visual.model.CollectedElementMetadata;
import com.visual.model.ElementSnapshot;
import com.visual.model.PageIdentity;
import com.visual.semantic.SemanticSignalExtractor;
import com.visual.semantic.SemanticSignals;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.awt.image.BufferedImage;

final class BaselineCaptureService {
    private final BaselineStore store;
    private final SemanticSignalExtractor semanticExtractor;
    private final LocalEmbeddingService embeddingService;
    private final CandidateCollector candidateCollector;
    private final PageIdentityService pageIdentityService;

    BaselineCaptureService(BaselineStore store, SemanticSignalExtractor semanticExtractor,
                           LocalEmbeddingService embeddingService, CandidateCollector candidateCollector) {
        this.store = store;
        this.semanticExtractor = semanticExtractor;
        this.embeddingService = embeddingService;
        this.candidateCollector = candidateCollector;
        this.pageIdentityService = new PageIdentityService();
    }

    void capture(WebDriver driver, WebElement element, By locator, boolean replaceExisting) {
        try {
            String pageUrl = driver.getCurrentUrl();
            CollectedElementMetadata meta = candidateCollector.metadata(driver, element);
            int x = meta.getX();
            int y = meta.getY();
            int w = meta.getW();
            int h = meta.getH();
            if (w <= 0 || h <= 0) {
                return;
            }

            BufferedImage page = ImageUtils.screenshotPage(driver);
            String screenshotBase64 = ImageUtils.toBase64(ImageUtils.crop(page, x, y, w, h));
            String text = meta.getText();
            String kind = meta.getKind();
            String tagName = meta.getTag();

            SemanticSignals signals = semanticExtractor.extract(driver, element);
            String accessibleName = firstNonBlank(signals.getAccessibleName(), meta.getAccessibleName());
            String semanticRole = firstNonBlank(signals.getSemanticRole(), meta.getSemanticRole());
            String autocomplete = firstNonBlank(signals.getAutocomplete(), meta.getAutocomplete());
            PageIdentity pageIdentity = pageIdentityService.capture(driver);

            ElementSnapshot snapshot = new ElementSnapshot(locator.toString(), screenshotBase64, x, y, w, h, text, pageUrl, kind, tagName,
                accessibleName, semanticRole, autocomplete)
                .withSemanticContext(
                    signals.getLabelText(),
                    signals.getPlaceholder(),
                    signals.getDescriptionText(),
                    signals.getSectionContext(),
                    signals.getParentContext(),
                    signals.getInputType()
                )
                .withPageIdentity(pageIdentity);

            String fingerprint = EmbeddingFingerprintBuilder.forSnapshot(locator.toString(), snapshot);
            float[] embeddingVector = embeddingService.embed(fingerprint);
            snapshot.withEmbedding(fingerprint, embeddingService.getModelName(), embeddingVector);

            boolean saved = store.save(snapshot, replaceExisting);
            if (saved) {
                System.out.println("[VISUAL-CAPTURE] " + locator + " page=" + pageUrl + " kind=" + kind + " role=" + semanticRole
                    + " autocomplete=" + autocomplete + " emb=" + (embeddingVector == null ? "off" : embeddingService.getModelName())
                    + " box=[" + x + "," + y + "," + w + "x" + h + "] text='" + text + "' accessible='" + accessibleName + "'");
            } else {
                System.out.println("[VISUAL-CAPTURE] Skipped existing baseline for " + locator + " page=" + pageUrl);
            }
        } catch (Exception e) {
            System.err.println("[VISUAL-CAPTURE] " + locator + ": " + e.getMessage());
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        String left = primary == null ? "" : primary.trim();
        String right = fallback == null ? "" : fallback.trim();
        return left.isBlank() ? right : left;
    }
}
