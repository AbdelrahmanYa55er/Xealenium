package com.demo.automationexercise;

import com.epam.healenium.SelfHealingDriver;
import com.visual.config.VisualHealingConfig;
import com.visual.config.XealeniumRuntimeProperties;
import com.visual.driver.VisualDriver;
import com.visual.engine.VisualHealingEngine;
import com.visual.report.ReportEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AutomationExerciseBaseTest {
    private static final String DEFAULT_BASELINE_FILE = "visual-baseline.json";
    private static final String DEFAULT_REPORT_FILE = "visual-healing-report.html";

    protected ChromeDriver chrome;
    protected VisualDriver driver;

    protected abstract boolean captureBaseline();

    protected abstract String flowName();

    @BeforeEach
    void setUpAutomationExerciseDriver() {
        configurePhase();
        VisualHealingEngine.REPORTS.clear();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        if (XealeniumRuntimeProperties.getBoolean("headless", false)) {
            options.addArguments("--headless=new", "--window-size=1440,1200");
        }

        chrome = new ChromeDriver(options);
        WebDriver recoveredDriver = createRecoveringDriver(chrome);
        VisualHealingConfig config = VisualHealingConfig.fromSystemProperties();
        driver = new VisualDriver(recoveredDriver, chrome, config);
    }

    @AfterEach
    void tearDownAutomationExerciseDriver() {
        try {
            if (XealeniumRuntimeProperties.getBoolean("report", true)) {
                VisualHealingEngine.generateHtmlReport(reportFile().toString());
            }
        } finally {
            if (driver != null) {
                driver.quit();
            } else if (chrome != null) {
                chrome.quit();
            }
            driver = null;
            chrome = null;
        }
    }

    protected void openBaselinePage(String fileName) {
        openPage(AutomationExercisePageSupport.baselineUrl(fileName), "Baseline/" + fileName);
    }

    protected void openUpdatedPage(String fileName) {
        openPage(AutomationExercisePageSupport.updatedUrl(fileName), "Updated/" + fileName);
    }

    protected void step(String action) {
        System.out.println("[AE-" + flowName() + "] " + action);
    }

    protected void assertNativeLookupFails(By locator) {
        try {
            chrome.findElement(locator);
        } catch (NoSuchElementException expected) {
            return;
        }
        throw new AssertionError("native Selenium should fail for drifted locator " + locator);
    }

    protected void assertBaselineContains(By locator) throws java.io.IOException {
        Path baselineFile = baselineFile();
        assertTrue(Files.exists(baselineFile), "baseline snapshot file should exist");
        String baselineJson = Files.readString(baselineFile);
        assertTrue(baselineJson.contains(locator.toString()), "baseline should contain " + locator);
    }

    protected void assertHealingArtifactsGenerated() {
        assertTrue(Files.exists(reportFile()), "HTML healing report should exist");
        assertTrue(!VisualHealingEngine.REPORTS.isEmpty(), "healing report entries should be collected");
        assertTrue(VisualHealingEngine.REPORTS.stream()
            .allMatch(entry -> Files.exists(Path.of(entry.heatmapFilename))),
            "each healing report entry should have a generated heatmap");
        String jsonReportPath = XealeniumRuntimeProperties.get("visual.report.json.path");
        if (!jsonReportPath.isBlank()) {
            assertTrue(Files.exists(Path.of(jsonReportPath)), "JSON healing report should exist");
        }
    }

    protected void printHealingSummary() {
        for (ReportEntry entry : VisualHealingEngine.REPORTS) {
            System.out.println("[AE-HEALING-SUMMARY] old=" + entry.originalLocator
                + " selected=\"" + entry.newLocator + "\""
                + " score=" + String.format(Locale.US, "%.3f", entry.score)
                + " heatmap=" + entry.heatmapFilename);
        }
    }

    protected void assertLatestHealingDidNotSelectGlobalNavigation(String context) {
        if (VisualHealingEngine.REPORTS.isEmpty()) {
            return;
        }
        String selected = VisualHealingEngine.REPORTS.get(VisualHealingEngine.REPORTS.size() - 1).newLocator;
        String normalized = selected == null ? "" : selected.toLowerCase(Locale.ROOT);
        boolean globalNavigation = normalized.contains("normalize-space()='home'")
            || normalized.contains("normalize-space()='products'")
            || normalized.contains("normalize-space()='cart'")
            || normalized.contains("normalize-space()='signup / login'");
        assertFalse(globalNavigation, context + " should not heal to global navigation: " + selected);
    }

    protected void assertLatestHealingSelected(String context, String... expectedFragments) {
        if (VisualHealingEngine.REPORTS.isEmpty()) {
            return;
        }
        String selected = VisualHealingEngine.REPORTS.get(VisualHealingEngine.REPORTS.size() - 1).newLocator;
        String normalized = selected == null ? "" : selected.toLowerCase(Locale.ROOT);
        boolean matched = false;
        for (String expectedFragment : expectedFragments) {
            if (normalized.contains(expectedFragment.toLowerCase(Locale.ROOT))) {
                matched = true;
                break;
            }
        }
        assertTrue(matched, context + " healed to unexpected target: " + selected);
    }

    protected void generateReportNow() {
        VisualHealingEngine.generateHtmlReport(reportFile().toString());
    }

    private void openPage(String url, String pageLabel) {
        driver.get(url);
        System.out.println("[AE-" + flowName() + "] Current page: " + pageLabel
            + " title=\"" + driver.getTitle() + "\" url=" + driver.getCurrentUrl());
    }

    private void configurePhase() {
        XealeniumRuntimeProperties.applyToSystemProperties(
            "report",
            "interactive",
            "headless",
            "visual.threshold",
            "visual.baseline.path",
            "visual.report.path",
            "visual.report.json.path",
            "visual.heatmap.dir",
            "visual.embedding.enabled",
            "visual.embedding.modelName",
            "visual.embedding.modelPath"
        );
        System.setProperty("report", System.getProperty("report", "true"));
        System.setProperty("interactive", System.getProperty("interactive", "false"));
        System.setProperty("visual.captureBaseline", String.valueOf(captureBaseline()));
        System.setProperty("visual.captureBaseline.refresh", String.valueOf(captureBaseline()));
    }

    private Path baselineFile() {
        return Path.of(XealeniumRuntimeProperties.get("visual.baseline.path", DEFAULT_BASELINE_FILE));
    }

    private Path reportFile() {
        return Path.of(XealeniumRuntimeProperties.get("visual.report.path", DEFAULT_REPORT_FILE));
    }

    private WebDriver createRecoveringDriver(ChromeDriver chromeDriver) {
        try {
            return SelfHealingDriver.create(chromeDriver);
        } catch (RuntimeException e) {
            System.out.println("[AE-" + flowName() + "] Healenium backend unavailable; continuing with Selenium + Xealenium: "
                + e.getMessage());
            return chromeDriver;
        }
    }
}
