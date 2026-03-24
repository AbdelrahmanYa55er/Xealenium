package com.demo.benchmark;

import com.epam.healenium.SelfHealingDriver;
import com.visual.driver.VisualDriver;
import com.visual.engine.VisualHealingEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HybridRecoveryModeTests extends BenchmarkPageSupport {
    private static final String BASELINE_PAGE = "baseline_hybrid.html";
    private static final String UPDATED_PAGE = "updated_hybrid.html";

    private ChromeDriver chrome;
    private SelfHealingDriver healingDriver;
    private VisualDriver visualDriver;
    private boolean interactiveMode;

    @Test
    void directLocatorsStillWorkOnHybridUpdatedPage() {
        BenchmarkScenarioCatalog.printSummaryOnce();
        setUpDrivers();
        primeBaseline();
        openUpdatedPage();

        for (FieldSpec field : directFields()) {
            WebElement element = assertDoesNotThrow(() -> chrome.findElement(field.locator()));
            assertSemanticTarget(chrome, field.id(), element, field.hints().toArray(String[]::new));
        }
    }

    @Test
    void healeniumRecoversSoftChangesOnHybridUpdatedPage() {
        BenchmarkScenarioCatalog.printSummaryOnce();
        setUpDrivers();
        primeBaseline();
        openUpdatedPage();

        for (FieldSpec field : healeniumFields()) {
            assertThrows(NoSuchElementException.class, () -> chrome.findElement(field.locator()));
            WebElement element = assertDoesNotThrow(() -> healingDriver.findElement(field.locator()));
            assertSemanticTarget(chrome, field.id(), element, field.hints().toArray(String[]::new));
        }
    }

    @Test
    void visualHealingRecoversHardChangesOnHybridUpdatedPage() {
        BenchmarkScenarioCatalog.printSummaryOnce();
        setUpDrivers();
        primeBaseline();
        openUpdatedPage();
        VisualDriver rawVisualDriver = rawVisualDriver();

        for (FieldSpec field : visualFields()) {
            assertThrows(NoSuchElementException.class, () -> chrome.findElement(field.locator()));

            int reportsBefore = VisualHealingEngine.REPORTS.size();
            WebElement element = assertDoesNotThrow(() -> rawVisualDriver.findElement(field.locator()));

            assertSemanticTarget(chrome, field.id(), element, field.hints().toArray(String[]::new));
            assertTrue(VisualHealingEngine.REPORTS.size() > reportsBefore,
                () -> "Expected visual healing report for " + field.id());
            assertTrue(VisualHealingEngine.REPORTS.stream().anyMatch(entry -> field.locator().toString().equals(entry.originalLocator)),
                () -> "Expected visual report entry for " + field.id());
        }
    }

    @Test
    void hybridPageCompletesEndToEndWithVisualDriver() throws Exception {
        BenchmarkScenarioCatalog.printSummaryOnce();
        setUpDrivers();
        primeBaseline();
        openUpdatedPage();
        VisualDriver rawVisualDriver = rawVisualDriver();

        type(rawVisualDriver.findElement(By.id("fname")), "Ava");
        type(rawVisualDriver.findElement(By.id("lname")), "Stone");
        type(rawVisualDriver.findElement(By.id("email")), "ava@workspace.io");
        type(rawVisualDriver.findElement(By.id("phone")), "+1 202 555 0184");
        WebElement city = rawVisualDriver.findElement(By.id("city"));
        assertSemanticTarget(chrome, "city", city, "metro area", "city name", "seattle");
        type(city, "Seattle");
        type(rawVisualDriver.findElement(By.id("zip")), "98101");

        WebElement country = rawVisualDriver.findElement(By.id("country"));
        assertNotNull(country);
        country.click();

        WebElement terms = rawVisualDriver.findElement(By.id("terms"));
        assertNotNull(terms);
        if (!terms.isSelected()) {
            terms.click();
        }

        WebElement newsletter = rawVisualDriver.findElement(By.id("newsletter"));
        assertNotNull(newsletter);
        if (!newsletter.isSelected()) {
            newsletter.click();
        }

        WebElement submit = rawVisualDriver.findElement(By.id("submit-btn"));
        assertSemanticTarget(chrome, "submit-btn", submit, "create workspace", "launch workspace", "workspace ready");
        submit.click();

        String status = (String) ((JavascriptExecutor) chrome).executeScript(
            "return document.getElementById('completion-state').textContent.trim();");
        assertEquals("Workspace ready", status);
    }

    private void setUpDrivers() {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        chrome = new ChromeDriver(opts);
        healingDriver = SelfHealingDriver.create(chrome);
        visualDriver = new VisualDriver(healingDriver, chrome);
        interactiveMode = Boolean.parseBoolean(System.getProperty("interactive", "false"));
        visualDriver.getEngine().setInteractiveMode(interactiveMode);
        VisualHealingEngine.REPORTS.clear();
    }

    private void primeBaseline() {
        visualDriver.getEngine().setCaptureBaseline(true);
        visualDriver.getEngine().setRefreshBaseline(true);
        visualDriver.get(pageUrl(BASELINE_PAGE));

        for (FieldSpec field : allFields()) {
            WebElement element = visualDriver.findElement(field.locator());
            assertNotNull(element, "Expected baseline element for " + field.id());
        }
    }

    private void openUpdatedPage() {
        visualDriver.getEngine().setCaptureBaseline(false);
        visualDriver.getEngine().setRefreshBaseline(false);
        visualDriver.get(pageUrl(UPDATED_PAGE));
    }

    private VisualDriver rawVisualDriver() {
        VisualDriver driver = new VisualDriver(chrome, chrome, visualDriver.getEngine());
        driver.getEngine().setInteractiveMode(interactiveMode);
        return driver;
    }

    private void type(WebElement element, String value) {
        try {
            String tag = element.getTagName().toLowerCase(Locale.ROOT);
            if ("input".equals(tag) || "textarea".equals(tag)) {
                element.clear();
            } else if ("true".equalsIgnoreCase(element.getAttribute("contenteditable"))) {
                ((JavascriptExecutor) chrome).executeScript("arguments[0].textContent='';", element);
            }
        } catch (Exception ignored) {
        }
        element.sendKeys(value);
    }

    private List<FieldSpec> directFields() {
        return List.of(
            field("fname", "preferred name", "ava"),
            field("email", "primary email", "workspace.io"),
            field("lname", "family name", "stone"),
            field("newsletter", "product updates"),
            field("zip", "postal route", "postal", "zip"),
            field("terms", "accept workspace terms", "workspace terms"),
            field("submit-btn", "create workspace")
        );
    }

    private List<FieldSpec> healeniumFields() {
        return List.of(
            field("phone", "phone number", "contact phone", "202 555"),
            field("country", "region", "country", "united states")
        );
    }

    private List<FieldSpec> visualFields() {
        return List.of(
            field("city", "metro area", "city name", "seattle")
        );
    }

    private List<FieldSpec> allFields() {
        return List.of(
            field("fname", "preferred name"),
            field("lname", "family name"),
            field("email", "primary email"),
            field("phone", "phone number"),
            field("city", "home city"),
            field("zip", "postal code"),
            field("country", "region"),
            field("terms", "workspace terms"),
            field("newsletter", "product updates"),
            field("submit-btn", "create workspace")
        );
    }

    private FieldSpec field(String id, String... hints) {
        return new FieldSpec(id, By.id(id), List.of(hints));
    }

    @AfterEach
    void tearDown() {
        if (visualDriver != null) {
            visualDriver.quit();
        } else if (healingDriver != null) {
            healingDriver.quit();
        } else if (chrome != null) {
            chrome.quit();
        }
        visualDriver = null;
        healingDriver = null;
        chrome = null;
    }

    private record FieldSpec(String id, By locator, List<String> hints) {
    }
}
