package com.visual;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

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
    void buildsStableIdLocatorForStandardInput() {
        driver.get(pageUrl("baseline.html"));
        int[] point = pointForCss("#email");
        SmartLocatorResult result = builder.buildLocatorFromPoint(point[0], point[1]);

        assertEquals("css", result.getLocatorType());
        assertEquals("id", result.getStrategy());
        assertTrue(result.getLocator().contains("email"));
        assertStable(result);
        assertUnique(result, driver);
    }

    @Test
    void buildsLabelBasedLocatorForContentEditableWithoutId() {
        driver.get(pageUrl("updated.html"));
        int[] point = pointForCss("div[contenteditable='true']");
        SmartLocatorResult result = builder.buildLocatorFromPoint(point[0], point[1]);

        assertEquals("xpath", result.getLocatorType());
        assertEquals("label-based", result.getStrategy());
        assertTrue(result.getLocator().contains("Given Name"));
        assertStable(result);
        assertUnique(result, driver);
    }

    @Test
    void normalizesInnerToggleBoxToMeaningfulClickableContainer() {
        driver.get(pageUrl("updated.html"));
        int[] point = pointForCss(".fake-toggle .toggle-box");
        SmartLocatorResult result = builder.buildLocatorFromPoint(point[0], point[1]);

        assertTrue(result.getLocator().contains("Agreement Signed") || result.getLocator().contains("fake-toggle"));
        assertFalse(result.getDetectedTag().equals("span"));
        assertStable(result);
        assertUnique(result, driver);
    }

    @Test
    void prefersOwnVisibleTextForSecondToggleInsteadOfPreviousSiblingLabel() {
        driver.get(pageUrl("updated.html"));
        int[] point = pointForIndexedSelector(".fake-toggle", 1);
        SmartLocatorResult result = builder.buildLocatorFromPoint(point[0], point[1]);

        assertTrue(List.of("class-text", "text", "class").contains(result.getStrategy()));
        assertFalse(result.getLocator().contains("Agreement Signed"));
        assertTrue(result.getLocator().contains("Email Feed") || result.getLocator().contains("fake-toggle"));
        assertStable(result);
        assertUnique(result, driver);
    }

    @Test
    void buildsReadableLocatorForSubmitLinkAfterDomChange() {
        driver.get(pageUrl("updated.html"));
        int[] point = pointForCss("a.submit-link");
        SmartLocatorResult result = builder.buildLocatorFromPoint(point[0], point[1]);

        assertTrue(List.of("text", "class-attribute", "class-text").contains(result.getStrategy()));
        assertFalse(result.getTopCandidates().isEmpty());
        assertStable(result);
        assertUnique(result, driver);
    }

    @Test
    void canBuildLocatorDirectlyFromElementForSubmitLink() {
        driver.get(pageUrl("updated.html"));
        WebElement submit = driver.findElement(By.cssSelector("a.submit-link"));
        SmartLocatorResult result = builder.buildLocatorForElement(submit);

        assertTrue(List.of("text", "class-attribute", "class-text").contains(result.getStrategy()));
        assertStable(result);
        assertUnique(result, driver);
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

    private static void assertStable(SmartLocatorResult result) {
        assertFalse(result.getLocator().contains("nth-of-type"));
        assertFalse(result.getLocator().startsWith("/html"));
        assertFalse(result.getLocator().startsWith("//html"));
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

    @SuppressWarnings("unchecked")
    private int[] pointForIndexedSelector(String selector, int index) {
        List<Number> coords = (List<Number>) ((JavascriptExecutor) driver).executeScript("""
            var els = document.querySelectorAll(arguments[0]);
            var el = els.length > arguments[1] ? els[arguments[1]] : null;
            if (!el) return null;
            var r = el.getBoundingClientRect();
            return [Math.round(r.left + (r.width / 2)), Math.round(r.top + (r.height / 2))];
            """, selector, index);
        assertNotNull(coords, "Could not find selector " + selector + " at index " + index + " to derive coordinates");
        return new int[]{coords.get(0).intValue(), coords.get(1).intValue()};
    }

    private static String pageUrl(String name) {
        return Path.of("pages", name).toAbsolutePath().toUri().toString();
    }
}
