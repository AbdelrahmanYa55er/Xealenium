package com.demo.automationexercise;

import com.visual.engine.VisualHealingEngine;
import com.visual.locator.SmartLocatorBuilder;
import com.visual.locator.SmartLocatorResult;
import com.visual.report.ReportEntry;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AutomationExercisePageSupport {
    protected final WebDriver driver;

    protected AutomationExercisePageSupport(WebDriver driver) {
        this.driver = driver;
    }

    public static String baselineUrl(String fileName) {
        return Path.of("pages", "AutomationExercise", "Baseline", fileName).toUri().toString();
    }

    public static String updatedUrl(String fileName) {
        return Path.of("pages", "AutomationExercise", "Updated", fileName).toUri().toString();
    }

    protected WebElement find(By locator) {
        int reportsBefore = VisualHealingEngine.REPORTS.size();
        WebElement element = driver.findElement(locator);
        assertNotNull(element, "element should exist for " + locator);
        logHealingIfPresent(locator, element, reportsBefore);
        return element;
    }

    protected void assertVisible(By locator, String description) {
        WebElement element = find(locator);
        assertTrue(element.isDisplayed(), description + " should be visible");
    }

    protected void click(By locator) {
        WebElement element = find(locator);
        scrollIntoView(element);
        preventStaticFixtureNavigation(element);
        try {
            element.click();
        } catch (RuntimeException clickFailure) {
            if (driver instanceof JavascriptExecutor js) {
                js.executeScript("arguments[0].click();", element);
            } else {
                throw clickFailure;
            }
        }
    }

    protected void type(By locator, String value) {
        WebElement element = find(locator);
        scrollIntoView(element);
        if (isContentEditable(element)) {
            setTextWithJavaScript(element, value);
            return;
        }
        try {
            element.clear();
            element.sendKeys(value);
        } catch (RuntimeException inputFailure) {
            setTextWithJavaScript(element, value);
        }
    }

    private void logHealingIfPresent(By locator, WebElement element, int reportsBefore) {
        if (VisualHealingEngine.REPORTS.size() <= reportsBefore) {
            return;
        }
        ReportEntry report = VisualHealingEngine.REPORTS.get(VisualHealingEngine.REPORTS.size() - 1);
        try {
            SmartLocatorResult smartLocator = new SmartLocatorBuilder(driver).buildLocatorForElement(element);
            System.out.println("[AE-HEALING] old=" + locator
                + " target=\"" + describeElement(element) + "\""
                + " score=" + String.format(Locale.US, "%.3f", report.score)
                + " selected=\"" + report.newLocator + "\""
                + " smart=\"" + smartLocator.getLocatorType() + ": " + smartLocator.getLocator() + "\""
                + " risk=" + smartLocator.getSelectedRiskLevel()
                + " reason=\"" + smartLocator.getSelectedReason() + "\"");
        } catch (RuntimeException auditFailure) {
            System.out.println("[AE-HEALING] old=" + locator
                + " score=" + String.format(Locale.US, "%.3f", report.score)
                + " selected=\"" + report.newLocator + "\"");
        }
    }

    private boolean isContentEditable(WebElement element) {
        String contentEditable = element.getAttribute("contenteditable");
        return "true".equalsIgnoreCase(contentEditable);
    }

    private void scrollIntoView(WebElement element) {
        if (driver instanceof JavascriptExecutor js) {
            js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});", element);
        }
    }

    private void preventStaticFixtureNavigation(WebElement element) {
        if (driver instanceof JavascriptExecutor js) {
            js.executeScript("""
                const element = arguments[0];
                document.addEventListener('click', event => {
                  if (event.target === element || element.contains(event.target)) {
                    event.preventDefault();
                  }
                }, { capture: true, once: true });
                const form = element.closest('form');
                if (form) {
                  form.addEventListener('submit', event => event.preventDefault(), { capture: true, once: true });
                }
                """, element);
        }
    }

    private void setTextWithJavaScript(WebElement element, String value) {
        if (!(driver instanceof JavascriptExecutor js)) {
            element.sendKeys(value);
            return;
        }
        js.executeScript("""
            const element = arguments[0];
            const value = arguments[1];
            if ('value' in element) {
              element.value = value;
            } else {
              element.textContent = value;
            }
            element.dispatchEvent(new Event('input', { bubbles: true }));
            element.dispatchEvent(new Event('change', { bubbles: true }));
            """, element, value);
    }

    private String describeElement(WebElement element) {
        if (!(driver instanceof JavascriptExecutor js)) {
            return element.getTagName();
        }
        Object raw = js.executeScript("""
            const element = arguments[0];
            const text = (element.innerText || element.textContent || '').replace(/\\s+/g, ' ').trim();
            return [
              element.tagName.toLowerCase(),
              element.id || '',
              element.getAttribute('role') || '',
              element.getAttribute('aria-label') || '',
              element.getAttribute('data-product-id') || '',
              text
            ].filter(Boolean).join(' | ');
            """, element);
        return raw == null ? "" : raw.toString();
    }
}
