package com.visual;

import com.visual.locator.SmartLocatorBuilder;
import com.visual.locator.SmartLocatorResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmartLocatorAutomationExerciseAuditTest {
    private static final Path AUTOMATION_EXERCISE_HTML = Path.of(
        System.getProperty("automation.exercise.path",
            "C:\\Users\\shady gamal\\OneDrive\\Desktop\\Automation_Exercise.html")
    );
    private static final List<AuditRow> AUDIT_ROWS = new ArrayList<>();

    private ChromeDriver driver;
    private SmartLocatorBuilder builder;

    @BeforeEach
    void setUp() {
        assertTrue(Files.exists(AUTOMATION_EXERCISE_HTML),
            "Expected local Automation_Exercise.html at " + AUTOMATION_EXERCISE_HTML);
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--headless=new", "--window-size=1440,1800", "--remote-allow-origins=*", "--no-sandbox");
        driver = new ChromeDriver(opts);
        builder = new SmartLocatorBuilder(driver);
        driver.get(AUTOMATION_EXERCISE_HTML.toUri().toString());
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @AfterAll
    static void printAuditReport() {
        System.out.println();
        System.out.println("=== SmartLocator Automation Exercise Audit ===");
        System.out.println("File: " + AUTOMATION_EXERCISE_HTML.toAbsolutePath());
        System.out.println("| Element description | Target | Selected locator | Risk | Unique? | Reason | Top alternatives | Verdict |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        for (AuditRow row : AUDIT_ROWS) {
            System.out.println("| " + md(row.description)
                + " | " + md(row.target)
                + " | " + md(row.selectedLocator)
                + " | " + row.risk
                + " | " + row.unique
                + " | " + md(row.reason)
                + " | " + md(row.topAlternatives())
                + " | " + row.verdict
                + " |");
        }
        System.out.println("=== End SmartLocator Automation Exercise Audit ===");
        System.out.println();
    }

    @Test
    void auditAutomationExerciseSmartLocatorBehavior() {
        auditSingletonIds();
        auditHeaderNavigationLinks();
        auditHeroCtas();
        auditCategoryAccordion();
        auditBrandLinks();
        auditSubscriptionForm();
        auditAddToCartLinks();
        auditViewProductLinks();
        auditRepeatedTextSafety();
        auditNoiseWidgetElements();
    }

    private void auditSingletonIds() {
        for (String id : List.of(
            "header",
            "slider",
            "slider-carousel",
            "accordian",
            "cartModal",
            "recommended-item-carousel",
            "footer",
            "susbscribe_email",
            "subscribe"
        )) {
            AuditRow row = audit("Singleton ID #" + id, "#" + id, driver.findElement(By.id(id)));
            assertEquals("css: #" + id, row.selectedLocator);
            assertEquals(SmartLocatorResult.RiskLevel.LOW, row.risk);
            assertTrue(row.reason.toLowerCase(Locale.ROOT).contains("id"));
            assertTrue(row.unique);
        }
    }

    private void auditHeaderNavigationLinks() {
        audit("Header nav Home", "a[href='/']", driver.findElement(By.cssSelector("header a[href='https://automationexercise.com/']")));
        AuditRow products = audit("Header nav Products", "a[href='/products']", driver.findElement(By.cssSelector("header a[href='https://automationexercise.com/products']")));
        assertTrue(products.selectedLocator.contains("/products") || products.selectedLocator.contains("Products"),
            "Products nav locator should use href or clean visible text");
        assertFalse(products.selectedLocator.contains("?"), "Products nav locator should not include icon glyph noise");
        assertTrue(products.risk == SmartLocatorResult.RiskLevel.MEDIUM || products.risk == SmartLocatorResult.RiskLevel.LOW);
        audit("Header nav Cart", "a[href='/view_cart']", driver.findElement(By.cssSelector("header a[href='https://automationexercise.com/view_cart']")));
        audit("Header nav Signup / Login", "a[href='/login']", driver.findElement(By.cssSelector("header a[href='https://automationexercise.com/login']")));
        audit("Header nav Test Cases", "a[href='/test_cases']", driver.findElement(By.cssSelector("header a[href='https://automationexercise.com/test_cases']")));
        audit("Header nav API Testing", "a[href='/api_list']", driver.findElement(By.cssSelector("header a[href='https://automationexercise.com/api_list']")));
        audit("Header nav Video Tutorials", "youtube link", driver.findElement(By.cssSelector("header a[href='https://www.youtube.com/c/AutomationExercise']")));
        audit("Header nav Contact us", "a[href='/contact_us']", driver.findElement(By.cssSelector("header a[href='https://automationexercise.com/contact_us']")));
    }

    private void auditHeroCtas() {
        List<WebElement> testCases = driver.findElements(By.cssSelector("a.test_cases_list"));
        List<WebElement> apiLists = driver.findElements(By.cssSelector("a.apis_list"));

        assertFalse(testCases.isEmpty(), "Expected hero Test Cases CTA links");
        assertFalse(apiLists.isEmpty(), "Expected hero APIs list CTA links");

        AuditRow testCasesRow = audit("Hero CTA Test Cases", "a.test_cases_list[0]", testCases.get(0));
        AuditRow apiListRow = audit("Hero CTA APIs list for practice", "a.apis_list[0]", apiLists.get(0));

        assertRepeatedSelectionIsNotLowIfNonUnique(testCasesRow);
        assertRepeatedSelectionIsNotLowIfNonUnique(apiListRow);
    }

    private void auditCategoryAccordion() {
        for (String id : List.of("Women", "Men", "Kids")) {
            audit("Category panel container #" + id, "#" + id, driver.findElement(By.id(id)));
            AuditRow toggle = audit("Category toggle " + id, "a[href='#" + id + "']", driver.findElement(By.cssSelector("a[href='https://automationexercise.com/#" + id + "']")));
            assertFalse("<builder failed>".equals(toggle.selectedLocator), "Category toggle should return an explainable locator");
            assertFalse(toggle.selectedLocator.isBlank());
        }
    }

    private void auditBrandLinks() {
        for (String brand : List.of("Polo", "H&M", "Biba")) {
            AuditRow row = audit("Brand link " + brand, "brand_products/" + brand,
                driver.findElement(By.cssSelector("a[href='https://automationexercise.com/brand_products/" + brand + "']")));
            assertFalse("<builder failed>".equals(row.selectedLocator), "Brand link should return an explainable locator");
            assertFalse(row.selectedLocator.isBlank());
        }
    }

    private void auditSubscriptionForm() {
        AuditRow email = audit("Subscription email input", "#susbscribe_email", driver.findElement(By.id("susbscribe_email")));
        AuditRow button = audit("Subscription submit button", "#subscribe", driver.findElement(By.id("subscribe")));

        assertEquals("css: #susbscribe_email", email.selectedLocator);
        assertEquals(SmartLocatorResult.RiskLevel.LOW, email.risk);
        assertTrue(email.unique);
        assertEquals("css: #subscribe", button.selectedLocator);
        assertEquals(SmartLocatorResult.RiskLevel.LOW, button.risk);
        assertTrue(button.unique);
    }

    private void auditAddToCartLinks() {
        for (String productId : List.of("1", "2", "3", "43")) {
            List<WebElement> matches = driver.findElements(By.cssSelector("a.add-to-cart[data-product-id='" + productId + "']"));
            assertFalse(matches.isEmpty(), "Expected add-to-cart link for product " + productId);
            AuditRow row = audit("Add to cart product " + productId,
                "a.add-to-cart[data-product-id='" + productId + "']",
                matches.get(0));
            row.note = "better=a.add-to-cart[data-product-id='" + productId + "']; dataProductCandidate="
                + hasCandidateContaining(row.result, "data-product-id");
            assertTrue(row.selectedLocator.equals("css: a.add-to-cart[data-product-id='" + productId + "']")
                || row.selectedLocator.equals("css: .features_items .productinfo a.add-to-cart[data-product-id='" + productId + "']")
                || row.selectedLocator.equals("css: .recommended_items .productinfo a.add-to-cart[data-product-id='" + productId + "']"));
            assertTrue(row.risk == SmartLocatorResult.RiskLevel.MEDIUM || row.risk == SmartLocatorResult.RiskLevel.LOW);
            assertTrue(row.reason.toLowerCase(Locale.ROOT).contains("data-product-id")
                || row.reason.toLowerCase(Locale.ROOT).contains("business"));
            assertTrue(row.unique);
            assertTrue(hasCandidateContaining(row.result, "data-product-id"));
            assertRepeatedSelectionIsNotLowIfNonUnique(row);
        }
    }

    private void auditViewProductLinks() {
        for (String productId : List.of("1", "2", "43")) {
            List<WebElement> matches = driver.findElements(By.cssSelector("a[href='https://automationexercise.com/product_details/" + productId + "']"));
            assertFalse(matches.isEmpty(), "Expected View Product link for product " + productId);
            AuditRow row = audit("View Product " + productId,
                "a[href*='/product_details/" + productId + "']",
                matches.get(0));
            row.note = "better=a[href*='/product_details/" + productId + "']; hrefCandidate="
                + hasCandidateContaining(row.result, "product_details/" + productId);
            assertTrue(row.selectedLocator.contains("/product_details/" + productId));
            assertEquals(SmartLocatorResult.RiskLevel.MEDIUM, row.risk);
            assertTrue(row.reason.toLowerCase(Locale.ROOT).contains("href"));
            assertTrue(row.unique);
            assertTrue(hasCandidateContaining(row.result, "product_details/" + productId));
            assertRepeatedSelectionIsNotLowIfNonUnique(row);
        }
    }

    private void auditRepeatedTextSafety() {
        AuditRow addToCart = audit("Repeated text Add to cart", "first text Add to cart",
            driver.findElements(By.xpath("//a[normalize-space()='Add to cart']")).get(0));
        AuditRow viewProduct = audit("Repeated text View Product", "first text View Product",
            driver.findElements(By.xpath("//a[normalize-space()='View Product']")).get(0));
        AuditRow testCases = audit("Repeated text Test Cases", "first text Test Cases",
            driver.findElements(By.xpath("//a[normalize-space()='Test Cases']")).get(0));

        assertRepeatedSelectionIsNotLowIfNonUnique(addToCart);
        assertRepeatedSelectionIsNotLowIfNonUnique(viewProduct);
        assertRepeatedSelectionIsNotLowIfNonUnique(testCases);
    }

    private void auditNoiseWidgetElements() {
        for (String id : List.of("WidgetFloater", "LanguageMenu", "ShareLink", "HelpLink")) {
            AuditRow row = audit("Noise/widget element #" + id, "#" + id, driver.findElement(By.id(id)));
            assertEquals("css: #" + id, row.selectedLocator);
            assertEquals(SmartLocatorResult.RiskLevel.LOW, row.risk);
            assertTrue(row.unique);
        }
    }

    private AuditRow audit(String description, String target, WebElement element) {
        SmartLocatorResult result;
        try {
            result = builder.buildLocatorForElement(element);
        } catch (RuntimeException e) {
            AuditRow row = new AuditRow(
                description,
                target,
                "<builder failed>",
                SmartLocatorResult.RiskLevel.HIGH,
                false,
                e.getClass().getSimpleName() + ": " + e.getMessage(),
                "",
                Verdict.NEEDS_IMPROVEMENT,
                null
            );
            AUDIT_ROWS.add(row);
            return row;
        }
        boolean unique = isUnique(result);
        String selected = result.getLocatorType() + ": " + result.getLocator();
        AuditRow row = new AuditRow(
            description,
            target,
            selected,
            result.getSelectedRiskLevel(),
            unique,
            result.getSelectedReason(),
            topCandidates(result),
            verdict(result, unique),
            result
        );
        AUDIT_ROWS.add(row);
        return row;
    }

    private void assertRepeatedSelectionIsNotLowIfNonUnique(AuditRow row) {
        if (!row.unique) {
            assertTrue(row.risk != SmartLocatorResult.RiskLevel.LOW,
                "Non-unique locator must not be LOW risk for " + row.description + ": " + row.selectedLocator);
        }
    }

    private boolean isUnique(SmartLocatorResult result) {
        try {
            return driver.findElements(byFor(result)).size() == 1;
        } catch (WebDriverException e) {
            return false;
        }
    }

    private static By byFor(SmartLocatorResult result) {
        return "xpath".equals(result.getLocatorType())
            ? By.xpath(result.getLocator())
            : By.cssSelector(result.getLocator());
    }

    private static String topCandidates(SmartLocatorResult result) {
        List<SmartLocatorResult.LocatorCandidate> candidates = result.getCandidates();
        int limit = Math.min(5, candidates.size());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            SmartLocatorResult.LocatorCandidate candidate = candidates.get(i);
            parts.add("#" + candidate.getRank()
                + " " + candidate.getType() + ":" + candidate.getValue()
                + " " + candidate.getRiskLevel()
                + " unique=" + candidate.isUnique()
                + " reason=" + candidate.getReason());
        }
        return String.join("; ", parts);
    }

    private static boolean hasCandidateContaining(SmartLocatorResult result, String value) {
        if (result == null) {
            return false;
        }
        return result.getCandidates().stream()
            .anyMatch(candidate -> candidate.getValue().contains(value));
    }

    private static Verdict verdict(SmartLocatorResult result, boolean unique) {
        if (unique && result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.LOW) {
            return Verdict.GOOD;
        }
        if (unique && result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.MEDIUM) {
            return Verdict.ACCEPTABLE;
        }
        if (!unique && result.getSelectedRiskLevel() != SmartLocatorResult.RiskLevel.LOW) {
            return Verdict.WEAK;
        }
        return Verdict.NEEDS_IMPROVEMENT;
    }

    private static String md(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ").trim();
    }

    private enum Verdict {
        GOOD,
        ACCEPTABLE,
        WEAK,
        NEEDS_IMPROVEMENT
    }

    private static final class AuditRow {
        final String description;
        final String target;
        final String selectedLocator;
        final SmartLocatorResult.RiskLevel risk;
        final boolean unique;
        final String reason;
        final String topAlternatives;
        final Verdict verdict;
        final SmartLocatorResult result;
        String note = "";

        AuditRow(String description, String target, String selectedLocator, SmartLocatorResult.RiskLevel risk,
                 boolean unique, String reason, String topAlternatives, Verdict verdict, SmartLocatorResult result) {
            this.description = description;
            this.target = target;
            this.selectedLocator = selectedLocator;
            this.risk = risk;
            this.unique = unique;
            this.reason = reason;
            this.topAlternatives = topAlternatives;
            this.verdict = verdict;
            this.result = result;
        }

        String topAlternatives() {
            if (note == null || note.isBlank()) {
                return topAlternatives;
            }
            return topAlternatives + "; observation=" + note;
        }
    }
}
