package com.demo.profile;

import com.epam.healenium.SelfHealingDriver;
import com.visual.config.XealeniumRuntimeProperties;
import com.visual.config.VisualHealingConfig;
import com.visual.driver.VisualDriver;
import com.visual.engine.VisualHealingEngine;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProfileHealingCycleTests {
    private static final Path BASELINE_FILE = Path.of("visual-baseline.json");
    private static final Path REPORT_FILE = Path.of("visual-healing-report.html");
    private static final List<By> PROFILE_LOCATORS = List.of(
        By.id("fullName"),
        By.id("userEmail"),
        By.id("mobileNumber"),
        By.id("jobTitle"),
        By.id("department"),
        By.id("notifications"),
        By.id("twoFactor"),
        By.id("saveProfileBtn")
    );

    private ChromeDriver chrome;
    private VisualDriver driver;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        } else if (chrome != null) {
            chrome.quit();
        }
    }

    @Test
    @Order(1)
    void captureProfileBaseline() throws IOException {
        Assumptions.assumeTrue(shouldRunPhase("capture"));
        configurePhase(true);
        startDriver();

        driver.get(pageUrl("profile_baseline.html"));
        System.out.println("[PROFILE-CYCLE] Loaded baseline " + driver.getCurrentUrl() + " title=" + driver.getTitle());

        WebElement fullName = driver.findElement(By.id("fullName"));
        fullName.sendKeys("Alex Morgan");
        assertNotNull(fullName);

        WebElement email = driver.findElement(By.id("userEmail"));
        email.sendKeys("alex.profile@example.com");
        assertNotNull(email);

        WebElement mobile = driver.findElement(By.id("mobileNumber"));
        mobile.sendKeys("+1 555 0100");
        assertNotNull(mobile);

        WebElement role = driver.findElement(By.id("jobTitle"));
        role.sendKeys("Product Manager");
        assertNotNull(role);

        WebElement department = driver.findElement(By.id("department"));
        department.click();
        assertNotNull(department);

        WebElement notifications = driver.findElement(By.id("notifications"));
        notifications.click();
        assertNotNull(notifications);

        WebElement twoFactor = driver.findElement(By.id("twoFactor"));
        twoFactor.click();
        assertNotNull(twoFactor);

        WebElement save = driver.findElement(By.id("saveProfileBtn"));
        save.click();
        assertNotNull(save);

        assertTrue(Files.exists(BASELINE_FILE), "baseline snapshot file should exist");
        String baselineJson = Files.readString(BASELINE_FILE);
        for (By locator : PROFILE_LOCATORS) {
            assertTrue(baselineJson.contains(locator.toString()), "baseline should contain " + locator);
        }
        System.out.println("[PROFILE-CYCLE] Baseline snapshots stored in " + BASELINE_FILE.toAbsolutePath());
    }

    @Test
    @Order(2)
    void healProfileUpdatedPage() {
        Assumptions.assumeTrue(shouldRunPhase("healing"));
        configurePhase(false);
        deleteReportArtifacts();
        VisualHealingEngine.REPORTS.clear();
        startDriver();

        driver.get(pageUrl("profile_updated.html"));
        System.out.println("[PROFILE-CYCLE] Loaded updated " + driver.getCurrentUrl() + " title=" + driver.getTitle());

        for (By locator : PROFILE_LOCATORS) {
            assertTrue(nativeLookupFails(locator), "native Selenium should fail for drifted locator " + locator);
        }

        assertDoesNotThrow(() -> {
            driver.findElement(By.id("fullName")).sendKeys("Alex Morgan");
            driver.findElement(By.id("userEmail")).sendKeys("alex.updated@example.com");
            driver.findElement(By.id("mobileNumber")).sendKeys("+1 555 0177");
            driver.findElement(By.id("jobTitle")).sendKeys("Principal Product Manager");
            driver.findElement(By.id("department")).sendKeys("Product");
            driver.findElement(By.id("notifications")).click();
            driver.findElement(By.id("twoFactor")).click();
            driver.findElement(By.id("saveProfileBtn")).click();
        });

        Object updated = ((JavascriptExecutor) chrome).executeScript("return window.profileUpdated === true;");
        assertTrue(Boolean.TRUE.equals(updated), "updated page action should be clicked");

        assertFalse(VisualHealingEngine.REPORTS.isEmpty(), "healing report entries should be collected");
        for (ReportEntry entry : VisualHealingEngine.REPORTS) {
            System.out.println("[PROFILE-CYCLE] healed " + entry.originalLocator + " -> "
                + entry.newLocator + " score=" + String.format("%.3f", entry.score));
        }

        VisualHealingEngine.generateHtmlReport(REPORT_FILE.toString());
        assertTrue(Files.exists(REPORT_FILE), "HTML healing report should exist");
        assertTrue(VisualHealingEngine.REPORTS.stream().allMatch(entry -> Files.exists(Path.of(entry.heatmapFilename))),
            "each healing entry should have a heatmap");
        System.out.println("[PROFILE-CYCLE] Report generated at " + REPORT_FILE.toAbsolutePath());
    }

    private void configurePhase(boolean captureBaseline) {
        XealeniumRuntimeProperties.applyToSystemProperties(
            "report",
            "interactive",
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
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        chrome = new ChromeDriver(opts);
        WebDriver recoveredDriver = createRecoveringDriver(chrome);
        VisualHealingConfig config = VisualHealingConfig.fromSystemProperties();
        driver = new VisualDriver(recoveredDriver, chrome, config);
    }

    private WebDriver createRecoveringDriver(ChromeDriver chromeDriver) {
        try {
            return SelfHealingDriver.create(chromeDriver);
        } catch (RuntimeException e) {
            System.out.println("[PROFILE-CYCLE] Healenium backend unavailable; continuing with Selenium + visual healing only: " + e.getMessage());
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

    private static boolean shouldRunPhase(String phase) {
        String configured = System.getProperty("profile.phase", "full").trim().toLowerCase();
        return configured.equals("full") || configured.equals(phase);
    }

    private static String pageUrl(String pageName) {
        return Path.of("pages", pageName).toAbsolutePath().toUri().toString();
    }

    private static void deleteReportArtifacts() {
        try {
            Files.deleteIfExists(REPORT_FILE);
        } catch (IOException ignored) {
        }
        for (String locatorName : List.of("fullName", "userEmail", "mobileNumber", "jobTitle", "department", "notifications", "twoFactor", "saveProfileBtn")) {
            String safe = "visual-heatmap-By_id__" + locatorName;
            try (var files = Files.list(Path.of("."))) {
                files
                    .filter(path -> path.getFileName().toString().startsWith(safe))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
            } catch (IOException ignored) {
            }
        }
    }
}
