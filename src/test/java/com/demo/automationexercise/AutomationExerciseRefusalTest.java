package com.demo.automationexercise;

import com.demo.automationexercise.pages.ProductsPage;
import com.visual.engine.VisualHealingEngine;
import com.visual.report.ReportEntry;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoSuchElementException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutomationExerciseRefusalTest extends AutomationExerciseBaseTest {
    @Override
    protected boolean captureBaseline() {
        return false;
    }

    @Override
    protected String flowName() {
        return "REFUSAL";
    }

    @Test
    void refusesWhenBlueTopAddToCartTargetIsRemoved() {
        ProductsPage productsPage = new ProductsPage(driver);

        openRefusalPage("products.html");
        step("Verify old Blue Top add-to-cart locator is drifted and the business target is absent");
        assertNativeLookupFails(ProductsPage.ADD_BLUE_TOP_TO_CART);

        assertThrows(NoSuchElementException.class, productsPage::addBlueTopToCart,
            "Xealenium should refuse instead of healing Blue Top add-to-cart to another product or navigation action");

        assertFalse(VisualHealingEngine.REPORTS.isEmpty(), "refusal should create a report entry");
        ReportEntry refusal = VisualHealingEngine.REPORTS.get(VisualHealingEngine.REPORTS.size() - 1);
        assertFalse(refusal.accepted, "refusal report entry should be marked as not accepted");
        assertTrue(refusal.newLocator.contains("ABORTED"), "refusal should be reported as ABORTED");
        assertTrue(refusal.score < driver.getEngine().getConfig().getThreshold(),
            "best candidate score should remain below the configured threshold");

        generateReportNow();
        assertTrue(Files.exists(Path.of(refusal.heatmapFilename)), "refusal heatmap should be generated");
        assertHealingArtifactsGenerated();
        printHealingSummary();
    }
}
