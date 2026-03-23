package com.demo;

import com.epam.healenium.SelfHealingDriver;
import com.visual.VisualDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VisualPageMatrixTests {
    private ChromeDriver chrome;
    private VisualDriver driver;

    @Test
    void healsAgainstUpdatedHtml() throws Exception {
        runScenario("updated.html");
    }

    @Test
    void healsAgainstUpdatedVariantHtml() throws Exception {
        runScenario("updated_variant.html");
    }

    private void runScenario(String targetPage) throws Exception {
        setUpDriver();
        try {
            driver.getEngine().setInteractiveMode(false);
            System.setProperty("visual.captureBaseline", "true");
            System.setProperty("visual.captureBaseline.refresh", "true");
            fillForm(pageUrl("baseline.html"));

            System.setProperty("visual.captureBaseline", "false");
            System.setProperty("visual.captureBaseline.refresh", "false");
            fillForm(pageUrl(targetPage));
        } finally {
            System.clearProperty("visual.captureBaseline");
            System.clearProperty("visual.captureBaseline.refresh");
        }
    }

    private void setUpDriver() {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        chrome = new ChromeDriver(opts);
        WebDriver healenium = SelfHealingDriver.create(chrome);
        driver = new VisualDriver(healenium, chrome);
    }

    private void fillForm(String url) throws Exception {
        driver.get(url);
        Thread.sleep(800);

        WebElement fname = driver.findElement(By.id("fname"));
        assertSemanticTarget("fname", fname, "first name", "given name", "forename", "fname");
        clearIfSupported(fname);
        fname.sendKeys("Visual");
        assertNotNull(fname);
        slow();

        WebElement lname = driver.findElement(By.id("lname"));
        assertSemanticTarget("lname", lname, "last name", "family name", "surname", "lname");
        clearIfSupported(lname);
        lname.sendKeys("Engine");
        assertNotNull(lname);
        slow();

        WebElement email = driver.findElement(By.id("email"));
        assertSemanticTarget("email", email, "email", "email address", "mail contact");
        clearIfSupported(email);
        email.sendKeys("visual@heal.io");
        assertNotNull(email);
        slow();

        WebElement phone = driver.findElement(By.id("phone"));
        assertSemanticTarget("phone", phone, "phone", "phone line", "telephone", "cell");
        clearIfSupported(phone);
        phone.sendKeys("555-VIZ");
        assertNotNull(phone);
        slow();

        WebElement city = driver.findElement(By.id("city"));
        assertSemanticTarget("city", city, "city", "city name", "town", "municipality");
        clearIfSupported(city);
        city.sendKeys("AI City");
        assertNotNull(city);
        slow();

        WebElement zip = driver.findElement(By.id("zip"));
        assertSemanticTarget("zip", zip, "zip", "zip code", "postal", "postal code", "postcode");
        clearIfSupported(zip);
        zip.sendKeys("00001");
        assertNotNull(zip);
        slow();

        WebElement country = driver.findElement(By.id("country"));
        assertSemanticTarget("country", country, "country", "nation", "location", "region");
        country.click();
        assertNotNull(country);
        slow();

        WebElement terms = driver.findElement(By.id("terms"));
        assertSemanticTarget("terms", terms, "terms", "agreement", "policy");
        terms.click();
        assertNotNull(terms);
        slow();

        WebElement newsletter = driver.findElement(By.id("newsletter"));
        assertSemanticTarget("newsletter", newsletter, "newsletter", "email feed", "mailing list", "updates");
        newsletter.click();
        assertNotNull(newsletter);
        slow();

        WebElement submitBtn = driver.findElement(By.id("submit-btn"));
        assertSemanticTarget("submit-btn", submitBtn, "register", "submit", "finish");
        submitBtn.click();
        assertNotNull(submitBtn);

        Thread.sleep(1200);
    }

    private void slow() throws Exception {
        Thread.sleep(400);
    }

    private void clearIfSupported(WebElement element) {
        try {
            String tag = element.getTagName();
            if ("input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag)) {
                element.clear();
            }
        } catch (Exception ignored) {
        }
    }

    private void assertSemanticTarget(String logicalName, WebElement element, String... expectedHints) {
        String description = semanticDescription(element);
        boolean matches = Arrays.stream(expectedHints)
            .map(this::normalize)
            .anyMatch(description::contains);
        assertTrue(matches, () -> "Expected " + logicalName + " to heal to a matching target, but got: " + description);
    }

    private String semanticDescription(WebElement element) {
        String script =
            "function push(parts, value) {" +
            "  if (value === null || value === undefined) return;" +
            "  value = String(value).trim();" +
            "  if (value) parts.push(value);" +
            "}" +
            "function nodeText(el) { return el && el.innerText ? el.innerText.trim() : ''; }" +
            "function attr(el, name) { return el ? String(el.getAttribute(name) || '').trim() : ''; }" +
            "function referencedText(ids) {" +
            "  if (!ids) return '';" +
            "  var parts = [];" +
            "  ids.split(/\\s+/).forEach(function(id) {" +
            "    if (!id) return;" +
            "    var ref = document.getElementById(id);" +
            "    if (ref) push(parts, nodeText(ref));" +
            "  });" +
            "  return parts.join(' ');" +
            "}" +
            "function labelLikeText(el) {" +
            "  if (!el) return '';" +
            "  var aria = referencedText(attr(el, 'aria-labelledby'));" +
            "  if (aria) return aria;" +
            "  var direct = attr(el, 'aria-label') || attr(el, 'data-label');" +
            "  if (direct) return direct;" +
            "  var txt = nodeText(el);" +
            "  if (!txt) return '';" +
            "  var tag = el.tagName.toLowerCase();" +
            "  var cls = attr(el, 'class').toLowerCase();" +
            "  if (tag === 'label' || tag === 'legend') return txt;" +
            "  if (cls.indexOf('label') >= 0 || cls.indexOf('title') >= 0) return txt;" +
            "  if (tag === 'span' || tag === 'div' || tag === 'p' || tag === 'strong') return txt;" +
            "  return '';" +
            "}" +
            "function isFieldLike(el) {" +
            "  if (!el) return false;" +
            "  var tag = el.tagName.toLowerCase();" +
            "  var type = attr(el, 'type').toLowerCase();" +
            "  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';" +
            "  if (editable || tag === 'textarea' || tag === 'select') return true;" +
            "  if (tag === 'input') return type !== 'hidden';" +
            "  return false;" +
            "}" +
            "function nearestLabelText(el) {" +
            "  var parts = [];" +
            "  push(parts, referencedText(attr(el, 'aria-labelledby')));" +
            "  var wrappedLabel = el.closest('label');" +
            "  push(parts, nodeText(wrappedLabel));" +
            "  if (el.id) {" +
            "    var byFor = document.querySelector('label[for=\"' + el.id + '\"]');" +
            "    push(parts, nodeText(byFor));" +
            "  }" +
            "  if (parts.length) return parts;" +
            "  var prev = el.previousElementSibling;" +
            "  while (prev) {" +
            "    var prevLabel = labelLikeText(prev);" +
            "    if (prevLabel) {" +
            "      push(parts, prevLabel);" +
            "      break;" +
            "    }" +
            "    if (isFieldLike(prev)) break;" +
            "    prev = prev.previousElementSibling;" +
            "  }" +
            "  return parts;" +
            "}" +
            "var el = arguments[0], parts = [];" +
            "push(parts, el.tagName);" +
            "push(parts, el.getAttribute('type'));" +
            "push(parts, el.getAttribute('role'));" +
            "push(parts, referencedText(attr(el, 'aria-labelledby')));" +
            "push(parts, el.getAttribute('placeholder'));" +
            "push(parts, el.getAttribute('data-placeholder'));" +
            "push(parts, el.getAttribute('aria-label'));" +
            "push(parts, el.getAttribute('name'));" +
            "push(parts, nodeText(el));" +
            "nearestLabelText(el).forEach(function(value) { push(parts, value); });" +
            "return [...new Set(parts)].join(' | ');";
        Object result = ((JavascriptExecutor) driver).executeScript(script, element);
        return normalize(result == null ? "" : String.valueOf(result));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String pageUrl(String name) {
        return Path.of("pages", name).toAbsolutePath().toUri().toString();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
        driver = null;
        chrome = null;
    }
}
