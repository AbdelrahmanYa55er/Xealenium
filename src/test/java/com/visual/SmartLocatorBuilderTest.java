package com.visual;

import com.visual.driver.VisualDriver;
import com.visual.locator.SmartLocatorBuilder;
import com.visual.locator.SmartLocatorResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmartLocatorBuilderTest {
    private ChromeDriver driver;
    private SmartLocatorBuilder builder;

    @BeforeEach
    void setUp() {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--headless=new", "--window-size=1440,1400", "--remote-allow-origins=*", "--no-sandbox");
        driver = new ChromeDriver(opts);
        builder = new SmartLocatorBuilder(driver);
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void idPriorityWinsForStableUniqueId() {
        driver.get(htmlUrl("""
            <div id="first_name_new" contenteditable="true"></div>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("first_name_new")));

        assertEquals("css", result.getLocatorType());
        assertEquals("#first_name_new", result.getLocator());
        assertEquals("id", result.getStrategy());
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertTrue(result.getSelectedReason().toLowerCase().contains("id"));
        assertUnique(result, driver);
    }

    @Test
    void testAttributePriorityBeatsBroaderLocators() {
        driver.get(htmlUrl("""
            <div data-testid="last-name" contenteditable="true"></div>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[data-testid='last-name']")));

        assertEquals("css", result.getLocatorType());
        assertEquals("[data-testid='last-name']", result.getLocator());
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertUnique(result, driver);
    }

    @Test
    void ariaLabelPrioritySelectsAccessibleCssLocator() {
        driver.get(htmlUrl("""
            <div role="checkbox" aria-label="Subscribe to newsletter"></div>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[aria-label='Subscribe to newsletter']")));

        assertEquals("css", result.getLocatorType());
        assertTrue(result.getLocator().contains("aria-label='Subscribe to newsletter'"));
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertUnique(result, driver);
    }

    @Test
    void contenteditableAfterSpanTitleUsesSpecificContextXPath() {
        driver.get(pageUrl("updated.html"));

        WebElement surname = driver.findElements(By.cssSelector("div[contenteditable='true']")).get(1);
        SmartLocatorResult result = builder.buildLocatorForElement(surname);

        String expected = "//div[contains(@class,'form-card')]//span[contains(@class,'e-title') and normalize-space()='Surname']/following-sibling::div[@contenteditable='true'][1]";
        assertEquals("xpath", result.getLocatorType());
        assertEquals(expected, result.getLocator());
        assertEquals("label-based", result.getStrategy());
        assertEquals(SmartLocatorResult.RiskLevel.MEDIUM, result.getSelectedRiskLevel());
        assertTrue(result.getSelectedReason().contains("label-based"));
        assertUnique(result, driver);
    }

    @Test
    void submitLinkUsesSpecificTextActionLocator() {
        driver.get(pageUrl("updated.html"));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("a.submit-link")));

        assertTrue(
            "css".equals(result.getLocatorType()) || "xpath".equals(result.getLocatorType()),
            "expected css or xpath selector");
        assertTrue(
            "a.submit-link".equals(result.getLocator())
                || "//a[contains(@class,'submit-link') and normalize-space()='Finish Registration']".equals(result.getLocator()));
        assertTrue(result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.LOW
            || result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.MEDIUM);
        assertUnique(result, driver);
    }

    @Test
    void avoidsBroadXPathWhenSpecificContextExists() {
        driver.get(pageUrl("updated.html"));

        WebElement surname = driver.findElements(By.cssSelector("div[contenteditable='true']")).get(1);
        SmartLocatorResult result = builder.buildLocatorForElement(surname);

        String broad = "//*[self::label or self::span or self::div][normalize-space()='Surname']/following-sibling::*[@contenteditable='true'][1]";
        assertNotEqualsTrimmed(broad, result.getLocator());
        assertFalse(result.getLocator().contains("following-sibling::*"));
        assertTrue(result.getLocator().contains("span[contains(@class,'e-title')"));
        assertTrue(result.getLocator().contains("following-sibling::div"));
    }

    @Test
    void duplicateLabelsPreferContainerSpecificLocator() {
        driver.get(htmlUrl("""
            <section class="profile-card">
              <span class="e-title">Surname</span>
              <div contenteditable="true"></div>
            </section>
            <section class="account-card">
              <span class="e-title">Surname</span>
              <div contenteditable="true"></div>
            </section>
            """));

        WebElement target = driver.findElements(By.cssSelector("section.account-card div[contenteditable='true']")).get(0);
        SmartLocatorResult result = builder.buildLocatorForElement(target);

        assertEquals("xpath", result.getLocatorType());
        assertTrue(result.getLocator().contains("account-card"));
        assertTrue(result.getCandidates().stream().allMatch(candidate ->
            candidate.isUnique() || candidate.getRiskLevel() == SmartLocatorResult.RiskLevel.HIGH));
        assertUnique(result, driver);
    }

    @Test
    void genericFallbackIsHighRiskWhenNoBetterSignalExists() {
        driver.get(htmlUrl("""
            <main>
              <div contenteditable="true"></div>
            </main>
            """));

        WebElement target = driver.findElement(By.cssSelector("main > div[contenteditable='true']"));
        SmartLocatorResult result = builder.buildLocatorForElement(target);

        assertEquals("xpath", result.getLocatorType());
        assertEquals(SmartLocatorResult.RiskLevel.HIGH, result.getSelectedRiskLevel());
        assertFalse(result.getCandidates().isEmpty());
    }

    @Test
    void visualDriverExposesSmartLocatorBuilderConvenienceMethod() {
        driver.get(pageUrl("baseline.html"));
        VisualDriver visualDriver = new VisualDriver(driver, driver);
        int[] point = pointForCss("#fname");
        SmartLocatorResult result = visualDriver.buildLocatorFromPoint(point[0], point[1]);

        assertEquals("id", result.getStrategy());
        assertUnique(result, driver);
    }

    @Test
    void extractsAccessibleNameRoleAndAutocompleteSignals() {
        driver.get(pageUrl("semantic_signals.html"));
        WebElement email = driver.findElement(By.cssSelector("input[type='email']"));
        SmartLocatorResult result = builder.buildLocatorForElement(email);

        assertEquals("Primary Email", result.getAccessibleName());
        assertEquals("textbox", result.getSemanticRole());
        assertEquals("email", result.getAutocomplete());
        assertTrue(List.of("aria-labelledby", "semantic-css").contains(result.getStrategy()));
        assertUnique(result, driver);
    }

    @Test
    void extractsTextboxRoleForContentEditableAriaLabelledElement() {
        driver.get(pageUrl("semantic_signals.html"));
        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("div[contenteditable='true']")));

        assertEquals("Short Bio", result.getAccessibleName());
        assertEquals("textbox", result.getSemanticRole());
        assertTrue(List.of("aria-labelledby", "label-based").contains(result.getStrategy()));
        assertUnique(result, driver);
    }

    private static void assertUnique(SmartLocatorResult result, WebDriver driver) {
        By by = "xpath".equals(result.getLocatorType())
            ? By.xpath(result.getLocator())
            : By.cssSelector(result.getLocator());
        List<WebElement> matches = driver.findElements(by);
        assertEquals(1, matches.size(), "locator must be unique");
    }

    @SuppressWarnings("unchecked")
    private int[] pointForCss(String selector) {
        List<Number> coords = (List<Number>) ((JavascriptExecutor) driver).executeScript("""
            var el = document.querySelector(arguments[0]);
            if (!el) return null;
            var r = el.getBoundingClientRect();
            return [Math.round(r.left + (r.width / 2)), Math.round(r.top + (r.height / 2))];
            """, selector);
        assertNotNull(coords, "Could not find selector " + selector + " to derive coordinates");
        return new int[]{coords.get(0).intValue(), coords.get(1).intValue()};
    }

    private static void assertNotEqualsTrimmed(String expected, String actual) {
        assertFalse(expected.trim().equals(actual.trim()), "did not expect locator " + actual);
    }

    private static String pageUrl(String name) {
        return Path.of("pages", name).toAbsolutePath().toUri().toString();
    }

    private static String htmlUrl(String body) {
        String html = "<!DOCTYPE html><html><body>" + body + "</body></html>";
        try {
            Path tempFile = Files.createTempFile("xealenium-smart-locator-", ".html");
            Files.writeString(tempFile, html, StandardCharsets.UTF_8);
            tempFile.toFile().deleteOnExit();
            return tempFile.toUri().toString();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary HTML fixture", e);
        }
    }
}
