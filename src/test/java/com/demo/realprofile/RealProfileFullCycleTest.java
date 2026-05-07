package com.demo.realprofile;

import com.epam.healenium.SelfHealingDriver;
import com.visual.config.XealeniumRuntimeProperties;
import com.visual.config.VisualHealingConfig;
import com.visual.driver.VisualDriver;
import com.visual.engine.VisualHealingEngine;
import com.visual.locator.SmartLocatorBuilder;
import com.visual.locator.SmartLocatorResult;
import com.visual.report.ReportEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealProfileFullCycleTest {
    private static final Path BASELINE_FILE = Path.of("visual-baseline.json");
    private static final Path REPORT_FILE = Path.of("visual-healing-report.html");
    private static final String BASELINE_PAGE = "real_profile_baseline.html";
    private static final String UPDATED_PAGE = "real_profile_updated.html";

    private static final List<FieldSpec> REAL_PROFILE_FIELDS = List.of(
        new FieldSpec("fullName", By.id("fullName"), "Riley Chen", true),
        new FieldSpec("email", By.id("email"), "riley.chen@example.com", true),
        new FieldSpec("phone", By.id("phone"), "+1 555 0142", true),
        new FieldSpec("jobTitle", By.id("jobTitle"), "Director of Product", true),
        new FieldSpec("department", By.id("department"), "Product", true),
        new FieldSpec("notifications", By.id("notifications"), "", false),
        new FieldSpec("twoFactor", By.id("twoFactor"), "", false),
        new FieldSpec("saveProfile", By.id("saveProfile"), "", false)
    );

    private ChromeDriver chrome;
    private VisualDriver driver;

    @Test
    @Order(1)
    void captureRealProfileBaseline() throws IOException {
        Assumptions.assumeTrue(shouldRunPhase("capture"));
        configurePhase(true);
        startDriver();

        driver.get(pageUrl(BASELINE_PAGE));
        System.out.println("[REAL-PROFILE] Loaded baseline " + driver.getCurrentUrl() + " title=" + driver.getTitle());

        for (FieldSpec field : REAL_PROFILE_FIELDS) {
            WebElement element = driver.findElement(field.locator());
            assertNotNull(element, "baseline element should exist for " + field.locator());
            interactLightly(element, field);
        }

        assertTrue(Files.exists(BASELINE_FILE), "baseline snapshot file should exist");
        String baselineJson = Files.readString(BASELINE_FILE);
        for (FieldSpec field : REAL_PROFILE_FIELDS) {
            assertTrue(baselineJson.contains(field.locator().toString()), "baseline should contain " + field.locator());
        }
        System.out.println("[REAL-PROFILE] Baseline capture complete -> " + BASELINE_FILE.toAbsolutePath());
    }

    @Test
    @Order(2)
    void healRealProfileUpdatedPage() {
        Assumptions.assumeTrue(shouldRunPhase("healing"));
        configurePhase(false);
        cleanupReportArtifacts();
        VisualHealingEngine.REPORTS.clear();
        startDriver();

        driver.get(pageUrl(UPDATED_PAGE));
        System.out.println("[REAL-PROFILE] Loaded updated " + driver.getCurrentUrl() + " title=" + driver.getTitle());

        for (FieldSpec field : REAL_PROFILE_FIELDS) {
            assertTrue(nativeLookupFails(field.locator()), "native Selenium should fail for drifted locator " + field.locator());
        }

        for (FieldSpec field : REAL_PROFILE_FIELDS) {
            WebElement healed = assertDoesNotThrow(() -> findViaXealenium(field),
                "Xealenium should heal " + field.locator());
            assertNotNull(healed, "healed element should not be null for " + field.locator());
            completeUpdatedPageAction(healed, field);
        }

        Object updated = ((JavascriptExecutor) chrome).executeScript("return window.realProfileUpdated === true;");
        assertTrue(Boolean.TRUE.equals(updated), "updated page save action should complete");
        assertFalse(VisualHealingEngine.REPORTS.isEmpty(), "healing report entries should be collected");

        VisualHealingEngine.generateHtmlReport(REPORT_FILE.toString());
        assertTrue(Files.exists(REPORT_FILE), "HTML healing report should exist");
        assertTrue(VisualHealingEngine.REPORTS.stream().allMatch(entry -> Files.exists(Path.of(entry.heatmapFilename))),
            "each healing report entry should have a generated heatmap");
        System.out.println("[REAL-PROFILE] Healing report complete -> " + REPORT_FILE.toAbsolutePath());
    }

    private WebElement findViaXealenium(FieldSpec field) {
        int reportsBefore = VisualHealingEngine.REPORTS.size();
        WebElement element = driver.findElement(field.locator());
        assertTrue(VisualHealingEngine.REPORTS.size() > reportsBefore,
            "visual healing should record a report entry for " + field.locator());

        ReportEntry report = VisualHealingEngine.REPORTS.get(VisualHealingEngine.REPORTS.size() - 1);
        SmartLocatorResult smartLocator = new SmartLocatorBuilder(chrome).buildLocatorForElement(element);
        String target = describeElement(element);
        System.out.println("[REAL-PROFILE-AUDIT] old=" + field.locator()
            + " target=\"" + target + "\""
            + " score=" + String.format(Locale.US, "%.3f", report.score)
            + " selected=\"" + report.newLocator + "\""
            + " smart=\"" + smartLocator.getLocatorType() + ": " + smartLocator.getLocator() + "\""
            + " risk=" + smartLocator.getSelectedRiskLevel()
            + " reason=\"" + smartLocator.getSelectedReason() + "\"");
        return element;
    }

    private void configurePhase(boolean captureBaseline) {
        XealeniumRuntimeProperties.applyToSystemProperties(
            "report",
            "interactive",
            "headless",
            "visual.threshold",
            "visual.embedding.enabled",
            "visual.embedding.modelName",
            "visual.embedding.modelPath"
        );
        System.setProperty("report", "true");
        System.setProperty("interactive", "false");
        System.setProperty("visual.captureBaseline", String.valueOf(captureBaseline));
        System.setProperty("visual.captureBaseline.refresh", String.valueOf(captureBaseline));
    }

    private void startDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        if (Boolean.parseBoolean(System.getProperty("headless", "false"))) {
            options.addArguments("--headless=new", "--window-size=1440,1200");
        }
        chrome = new ChromeDriver(options);
        WebDriver recoveredDriver = createRecoveringDriver(chrome);
        VisualHealingConfig config = VisualHealingConfig.fromSystemProperties();
        driver = new VisualDriver(recoveredDriver, chrome, config);
    }

    private WebDriver createRecoveringDriver(ChromeDriver chromeDriver) {
        try {
            return SelfHealingDriver.create(chromeDriver);
        } catch (RuntimeException e) {
            System.out.println("[REAL-PROFILE] Healenium backend unavailable; continuing with Selenium + Xealenium: " + e.getMessage());
            return chromeDriver;
        }
    }

    private boolean nativeLookupFails(By locator) {
        try {
            chrome.findElement(locator);
            return false;
        } catch (NoSuchElementException expected) {
            return true;
        }
    }

    private void interactLightly(WebElement element, FieldSpec field) {
        if (field.textValue().isBlank()) {
            element.click();
            return;
        }
        try {
            String tag = element.getTagName().toLowerCase(Locale.ROOT);
            if ("input".equals(tag) || "textarea".equals(tag)) {
                element.clear();
                element.sendKeys(field.textValue());
            } else if ("select".equals(tag)) {
                element.click();
            } else {
                element.click();
            }
        } catch (RuntimeException ignored) {
            element.click();
        }
    }

    private void completeUpdatedPageAction(WebElement element, FieldSpec field) {
        if (field.textValue().isBlank()) {
            element.click();
            return;
        }
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        String contentEditable = element.getAttribute("contenteditable");
        if ("input".equals(tag) || "textarea".equals(tag)) {
            element.clear();
            element.sendKeys(field.textValue());
            return;
        }
        if ("true".equalsIgnoreCase(contentEditable)) {
            ((JavascriptExecutor) chrome).executeScript("arguments[0].textContent = '';", element);
            element.sendKeys(field.textValue());
            return;
        }
        element.click();
    }

    private String describeElement(WebElement element) {
        Object raw = ((JavascriptExecutor) chrome).executeScript("""
            var el = arguments[0];
            function text(node) {
              return node && node.innerText ? String(node.innerText).replace(/\\s+/g, ' ').trim() : '';
            }
            return [
              el.tagName.toLowerCase(),
              el.id || '',
              el.getAttribute('role') || '',
              el.getAttribute('aria-label') || '',
              text(el)
            ].filter(Boolean).join(' | ');
            """, element);
        return raw == null ? "" : raw.toString();
    }

    private static String pageUrl(String pageName) {
        return Path.of("pages", pageName).toAbsolutePath().toUri().toString();
    }

    private static boolean shouldRunPhase(String phase) {
        String configured = System.getProperty("real.profile.phase", "full").trim().toLowerCase(Locale.ROOT);
        return configured.equals("full") || configured.equals(phase);
    }

    private static void cleanupReportArtifacts() {
        try {
            Files.deleteIfExists(REPORT_FILE);
        } catch (IOException ignored) {
        }
        try (var files = Files.list(Path.of("."))) {
            files
                .filter(path -> path.getFileName().toString().startsWith("visual-heatmap-By_id__"))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        } else if (chrome != null) {
            chrome.quit();
        }
        driver = null;
        chrome = null;
    }

    private record FieldSpec(String id, By locator, String textValue, boolean textEntry) {
    }
}
