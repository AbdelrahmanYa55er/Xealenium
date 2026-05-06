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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void uniqueIdSelectorIsSelectedAsLowRisk() {
        driver.get(htmlUrl("""
            <input id="email_new" type="email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("email_new")));

        assertSelected(result, "css", "#email_new");
        assertEquals("id", result.getStrategy());
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertTrue(result.getSelectedReason().toLowerCase().contains("id"));
        assertUnique(result, driver);
    }

    @Test
    void dataTestIdSelectorIsSelectedBeforeXPath() {
        driver.get(htmlUrl("""
            <input data-testid="email-field" type="email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[data-testid='email-field']")));

        assertSelected(result, "css", "[data-testid='email-field']");
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertTrue(reasonContains(result, "data-testid") || reasonContains(result, "stable test attribute"));
        assertFalse("xpath".equals(result.getLocatorType()));
        assertUnique(result, driver);
    }

    @Test
    void dataQaSelectorIsSelectedAsLowRisk() {
        driver.get(htmlUrl("""
            <button data-qa="save-profile">Save</button>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[data-qa='save-profile']")));

        assertSelected(result, "css", "[data-qa='save-profile']");
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertUnique(result, driver);
    }

    @Test
    void businessDataProductIdBeatsRepeatedActionText() {
        driver.get(htmlUrl("""
            <a data-product-id="1" class="btn btn-default add-to-cart">Add to cart</a>
            <a data-product-id="2" class="btn btn-default add-to-cart">Add to cart</a>
            <a data-product-id="43" class="btn btn-default add-to-cart">Add to cart</a>
            """));

        for (String productId : List.of("1", "2", "43")) {
            SmartLocatorResult result = builder.buildLocatorForElement(
                driver.findElement(By.cssSelector("a[data-product-id='" + productId + "']")));

            assertSelected(result, "css", "a.add-to-cart[data-product-id='" + productId + "']");
            assertTrue(result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.LOW
                || result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.MEDIUM);
            assertTrue(reasonContains(result, "data-product-id") || reasonContains(result, "business"));
            assertTrue(candidateRank(result, "a.add-to-cart[data-product-id='" + productId + "']")
                < candidateRank(result, "//a[contains(@class,'btn') and normalize-space()='Add to cart']"));
            assertUnique(result, driver);
        }
    }

    @Test
    void productDetailHrefBeatsRepeatedViewProductText() {
        driver.get(htmlUrl("""
            <a href="https://automationexercise.com/product_details/1">View Product</a>
            <a href="https://automationexercise.com/product_details/2">View Product</a>
            <a href="https://automationexercise.com/product_details/43">View Product</a>
            """));

        for (String productId : List.of("1", "2", "43")) {
            SmartLocatorResult result = builder.buildLocatorForElement(
                driver.findElement(By.cssSelector("a[href$='/product_details/" + productId + "']")));

            assertSelected(result, "css", "a[href$='/product_details/" + productId + "']");
            assertEquals(SmartLocatorResult.RiskLevel.MEDIUM, result.getSelectedRiskLevel());
            assertTrue(reasonContains(result, "href"));
            assertTrue(candidateRank(result, "a[href$='/product_details/" + productId + "']")
                < candidateRank(result, "//a[normalize-space()='View Product']"));
            assertUnique(result, driver);
        }
    }

    @Test
    void hrefCandidateAvoidsIconTextPollution() {
        driver.get(htmlUrl("""
            <a href="https://automationexercise.com/products"><i class="material-icons">?</i> Products</a>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("a")));

        assertSelected(result, "css", "a[href$='/products']");
        assertEquals(SmartLocatorResult.RiskLevel.MEDIUM, result.getSelectedRiskLevel());
        assertFalse(result.getLocator().contains("?"));
        assertTrue(reasonContains(result, "href"));
        assertUnique(result, driver);
    }

    @Test
    void additionalStableTestAttributesAreSelectedAsLowRisk() {
        assertSelectedLocatorForHtml("""
            <input data-test="email-field">
            """, "[data-test='email-field']", "css", "[data-test='email-field']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <button data-cy="save-profile">Save</button>
            """, "[data-cy='save-profile']", "css", "[data-cy='save-profile']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <button data-automation="save-profile">Save</button>
            """, "[data-automation='save-profile']", "css", "[data-automation='save-profile']", SmartLocatorResult.RiskLevel.LOW);
    }

    @Test
    void ariaLabelButtonSelectorIsSelectedAsLowRisk() {
        driver.get(htmlUrl("""
            <button aria-label="Save profile"></button>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("button")));

        assertSelected(result, "css", "[aria-label='Save profile']");
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertUnique(result, driver);
    }

    @Test
    void ariaCheckboxSelectorIsSelectedAsLowRisk() {
        driver.get(htmlUrl("""
            <div role="checkbox" aria-label="Subscribe to newsletter" tabindex="0"></div>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[aria-label='Subscribe to newsletter']")));

        assertEquals("css", result.getLocatorType());
        assertTrue(List.of(
            "[role='checkbox'][aria-label='Subscribe to newsletter']",
            "[aria-label='Subscribe to newsletter']"
        ).contains(result.getLocator()));
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertTrue(reasonContains(result, "aria"));
        assertUnique(result, driver);
    }

    @Test
    void ariaLabelledBySelectorIsSupportedAndExplainable() {
        driver.get(htmlUrl("""
            <span id="emailLabel">Email Address</span>
            <input aria-labelledby="emailLabel">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[aria-labelledby='emailLabel']")));

        assertSelected(result, "css", "[aria-labelledby='emailLabel']");
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertTrue(reasonContains(result, "aria-labelledby"));
        assertEquals("Email Address", result.getAccessibleName());
        assertUnique(result, driver);
    }

    @Test
    void nameAttributeSelectorIsSelectedWithExplainableRisk() {
        driver.get(htmlUrl("""
            <input name="userEmail" type="email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[name='userEmail']")));

        assertSelected(result, "css", "[name='userEmail']");
        assertTrue(result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.LOW
            || result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.MEDIUM);
        assertTrue(reasonContains(result, "name"));
        assertUnique(result, driver);
    }

    @Test
    void genericNameDoesNotBeatAriaLabel() {
        driver.get(htmlUrl("""
            <input name="field" aria-label="Email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[name='field']")));

        assertSelected(result, "css", "[aria-label='Email']");
        assertCandidate(result, "css", "[name='field']");
        assertTrue(candidateRank(result, "[aria-label='Email']") < candidateRank(result, "[name='field']"));
        assertUnique(result, driver);
    }

    @Test
    void uniqueInputTypeSelectorIsDowngradedFromLowRisk() {
        driver.get(htmlUrl("""
            <input type="email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("input[type='email']")));

        assertSelected(result, "css", "input[type='email']");
        assertTrue(result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.MEDIUM
            || result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.HIGH);
        assertUnique(result, driver);
    }

    @Test
    void nonUniqueInputTypeCandidateIsNotLowRiskSafe() {
        driver.get(htmlUrl("""
            <input type="email">
            <input type="email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElements(By.cssSelector("input[type='email']")).get(0));
        SmartLocatorResult.LocatorCandidate typeCandidate = candidateOrNull(result, "input[type='email']");

        assertNotNull(typeCandidate);
        assertFalse(typeCandidate.isUnique());
        assertEquals(SmartLocatorResult.RiskLevel.HIGH, typeCandidate.getRiskLevel());
        assertFalse("input[type='email']".equals(result.getLocator())
            && result.getSelectedRiskLevel() != SmartLocatorResult.RiskLevel.HIGH);
    }

    @Test
    void generatedAndUtilityClassesDoNotBeatAriaLabel() {
        assertSelectedLocatorForHtml("""
            <input class="MuiInputBase-input" aria-label="Email">
            """, ".MuiInputBase-input", "css", "[aria-label='Email']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <button class="css-1abcxyz" aria-label="Save"></button>
            """, ".css-1abcxyz", "css", "[aria-label='Save']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <button class="sc-kpDqfm" aria-label="Save"></button>
            """, ".sc-kpDqfm", "css", "[aria-label='Save']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <button class="flex p-2 mt-4 text-sm" aria-label="Save"></button>
            """, "button", "css", "[aria-label='Save']", SmartLocatorResult.RiskLevel.LOW);
    }

    @Test
    void basicLabelBasedXPathUsesLabelTagAndInputTarget() {
        driver.get(htmlUrl("""
            <label>Email Address</label><input>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("input")));

        assertSelected(result, "xpath", "//label[normalize-space()='Email Address']/following-sibling::input[self::input][1]");
        assertEquals(SmartLocatorResult.RiskLevel.MEDIUM, result.getSelectedRiskLevel());
        assertTrue(reasonContains(result, "label-based"));
        assertUnique(result, driver);
    }

    @Test
    void contenteditableAfterSpanTitleUsesSpecificContextXPath() {
        driver.get(htmlUrl("""
            <div class="form-card">
              <span class="e-title">Surname</span>
              <div contenteditable="true" tabindex="0"></div>
            </div>
            """));

        WebElement surname = driver.findElement(By.cssSelector("div[contenteditable='true']"));
        SmartLocatorResult result = builder.buildLocatorForElement(surname);

        String expected = "//div[contains(@class,'form-card')]//span[contains(@class,'e-title') and normalize-space()='Surname']/following-sibling::div[@contenteditable='true'][1]";
        assertSelected(result, "xpath", expected);
        assertEquals("label-based", result.getStrategy());
        assertEquals(SmartLocatorResult.RiskLevel.MEDIUM, result.getSelectedRiskLevel());
        assertTrue(result.getSelectedReason().contains("label-based"));
        assertTrue(result.getSelectedReason().contains("contenteditable"));
        assertNotEqualsTrimmed(
            "//*[self::label or self::span or self::div][normalize-space()='Surname']/following-sibling::*[@contenteditable='true'][1]",
            result.getLocator());
        assertUnique(result, driver);
    }

    @Test
    void classicSelectAndNativeCheckboxUseStableIds() {
        assertSelectedLocatorForHtml("""
            <select id="department"></select>
            """, "#department", "css", "#department", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <input type="checkbox" id="terms">
            """, "#terms", "css", "#terms", SmartLocatorResult.RiskLevel.LOW);
    }

    @Test
    void customRoleControlsPreferRoleAriaLocator() {
        assertSelectedLocatorForHtml("""
            <div role="combobox" aria-label="Department"></div>
            """, "[role='combobox']", "css", "[role='combobox'][aria-label='Department']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <div role="checkbox" aria-label="I accept terms"></div>
            """, "[role='checkbox']", "css", "[role='checkbox'][aria-label='I accept terms']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <div role="switch" aria-label="Enable notifications"></div>
            """, "[role='switch']", "css", "[role='switch'][aria-label='Enable notifications']", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <div role="button" aria-label="Submit"></div>
            """, "[role='button']", "css", "[role='button'][aria-label='Submit']", SmartLocatorResult.RiskLevel.LOW);
    }

    @Test
    void nativeRadioUsesMeaningfulNameLocator() {
        driver.get(htmlUrl("""
            <input type="radio" name="gender" value="male">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("input[type='radio']")));

        assertSelected(result, "css", "[name='gender']");
        assertTrue(reasonContains(result, "name"));
        assertUnique(result, driver);
    }

    @Test
    void buttonTextUsesTagSpecificActionXPath() {
        driver.get(htmlUrl("""
            <button>Save Profile</button>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("button")));

        assertSelected(result, "xpath", "//button[normalize-space()='Save Profile']");
        assertTrue(reasonContains(result, "text"));
        assertUnique(result, driver);
    }

    @Test
    void submitLinkUsesSpecificTextActionLocator() {
        driver.get(htmlUrl("""
            <a class="submit-link" tabindex="0">Update Account</a>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("a.submit-link")));

        assertSelected(result, "xpath", "//a[contains(@class,'submit-link') and normalize-space()='Update Account']");
        assertTrue(reasonContains(result, "text action") || reasonContains(result, "action locator")
            || reasonContains(result, "text-based action"));
        assertTrue(result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.LOW
            || result.getSelectedRiskLevel() == SmartLocatorResult.RiskLevel.MEDIUM);
        assertUnique(result, driver);
    }

    @Test
    void buttonTextNormalizesWhitespaceInXPath() {
        driver.get(htmlUrl("""
            <button>
               Save
               Profile
            </button>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("button")));

        assertSelected(result, "xpath", "//button[normalize-space()='Save Profile']");
        assertUnique(result, driver);
    }

    @Test
    void idSelectorBeatsDataTestIdButKeepsDataTestIdCandidate() {
        driver.get(htmlUrl("""
            <input id="first_name_new" data-testid="first-name-field">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("first_name_new")));

        assertSelected(result, "css", "#first_name_new");
        assertCandidate(result, "css", "[data-testid='first-name-field']");
        assertTrue(candidateRank(result, "#first_name_new") < candidateRank(result, "[data-testid='first-name-field']"));
        assertUnique(result, driver);
    }

    @Test
    void stableSemanticIdIsStillSelectedBeforeAriaLabel() {
        driver.get(htmlUrl("""
            <input id="email_new" aria-label="Email Address">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("email_new")));

        assertSelected(result, "css", "#email_new");
        assertEquals(SmartLocatorResult.RiskLevel.LOW, result.getSelectedRiskLevel());
        assertUnique(result, driver);
    }

    @Test
    void muiGeneratedIdDoesNotBeatAriaLabel() {
        driver.get(htmlUrl("""
            <input id="mui-123" aria-label="Email Address">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("mui-123")));

        assertSelected(result, "css", "[aria-label='Email Address']");
        assertGeneratedIdCandidateIsHighRiskIfPresent(result, "#mui-123");
        assertUnique(result, driver);
    }

    @Test
    void materialGeneratedIdDoesNotBeatDataTestId() {
        driver.get(htmlUrl("""
            <input id="mat-input-17" data-testid="email-field">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("mat-input-17")));

        assertSelected(result, "css", "[data-testid='email-field']");
        assertGeneratedIdCandidateIsHighRiskIfPresent(result, "#mat-input-17");
        assertUnique(result, driver);
    }

    @Test
    void reactSelectGeneratedIdIsDowngraded() {
        driver.get(htmlUrl("""
            <input id="react-select-5-input" aria-label="Country">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("react-select-5-input")));

        assertSelected(result, "css", "[aria-label='Country']");
        assertGeneratedIdCandidateIsHighRiskIfPresent(result, "#react-select-5-input");
        assertUnique(result, driver);
    }

    @Test
    void reactUseIdStyleIdIsDowngraded() {
        driver.get(htmlUrl("""
            <input id=":r3:" aria-label="Search">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id(":r3:")));

        assertSelected(result, "css", "[aria-label='Search']");
        assertGeneratedIdCandidateIsHighRiskIfPresent(result, "[id=':r3:']");
        assertUnique(result, driver);
    }

    @Test
    void emberGeneratedIdIsDowngradedBelowName() {
        driver.get(htmlUrl("""
            <input id="ember421" name="username">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("ember421")));

        assertSelected(result, "css", "[name='username']");
        assertGeneratedIdCandidateIsHighRiskIfPresent(result, "#ember421");
        assertUnique(result, driver);
    }

    @Test
    void stableNumericSemanticIdIsNotFalselyRejected() {
        assertSelectedLocatorForHtml("""
            <input id="addressLine1" aria-label="Address Line 1">
            """, "#addressLine1", "css", "#addressLine1", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <input id="phone2" aria-label="Phone 2">
            """, "#phone2", "css", "#phone2", SmartLocatorResult.RiskLevel.LOW);

        assertSelectedLocatorForHtml("""
            <button id="step1Submit">Submit</button>
            """, "#step1Submit", "css", "#step1Submit", SmartLocatorResult.RiskLevel.LOW);
    }

    @Test
    void uuidLikeIdIsDowngradedBelowAriaLabel() {
        driver.get(htmlUrl("""
            <input id="f47ac10b-58cc-4372-a567-0e02b2c3d479" aria-label="Email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("f47ac10b-58cc-4372-a567-0e02b2c3d479")));

        assertSelected(result, "css", "[aria-label='Email']");
        assertGeneratedIdCandidateIsHighRiskIfPresent(result, "#f47ac10b-58cc-4372-a567-0e02b2c3d479");
        assertUnique(result, driver);
    }

    @Test
    void longRandomHexLikeIdIsDowngradedBelowAriaLabel() {
        driver.get(htmlUrl("""
            <input id="a8f31c9d77e24591" aria-label="Phone">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("a8f31c9d77e24591")));

        assertSelected(result, "css", "[aria-label='Phone']");
        assertGeneratedIdCandidateIsHighRiskIfPresent(result, "#a8f31c9d77e24591");
        assertUnique(result, driver);
    }

    @Test
    void generatedIdCandidateMetadataExplainsDowngradeWhenPresent() {
        driver.get(htmlUrl("""
            <input id="mui-123" aria-label="Email Address">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("mui-123")));
        SmartLocatorResult.LocatorCandidate generatedId = candidateOrNull(result, "#mui-123");

        assertFalse(result.getCandidates().isEmpty());
        if (generatedId != null) {
            assertEquals(SmartLocatorResult.RiskLevel.HIGH, generatedId.getRiskLevel());
            assertTrue(candidateReasonContains(generatedId, "generated")
                || candidateReasonContains(generatedId, "unstable"));
        } else {
            assertTrue(result.getLogs().stream().anyMatch(log ->
                log.toLowerCase().contains("mui-123") && (
                    log.toLowerCase().contains("generated")
                        || log.toLowerCase().contains("unstable")
                        || log.toLowerCase().contains("dynamic"))));
        }
    }

    @Test
    void dataTestIdBeatsLabelBasedXPathWhenBothExist() {
        driver.get(htmlUrl("""
            <div class="form-card">
              <span class="e-title">Email</span>
              <input data-testid="email-field" type="email">
            </div>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("[data-testid='email-field']")));

        assertSelected(result, "css", "[data-testid='email-field']");
        assertTrue(result.getCandidates().stream().anyMatch(candidate ->
            "xpath".equals(candidate.getType()) && candidate.getReason().contains("label-based")));
        assertUnique(result, driver);
    }

    @Test
    void duplicateLabelsDoNotSelectBroadUnsafeLocatorAsLowOrMediumRisk() {
        driver.get(htmlUrl("""
            <div class="form-card">
              <span class="e-title">Email</span>
              <div contenteditable="true"></div>
            </div>
            <div class="form-card">
              <span class="e-title">Email</span>
              <div contenteditable="true"></div>
            </div>
            """));

        WebElement target = driver.findElements(By.cssSelector("div[contenteditable='true']")).get(0);
        SmartLocatorResult result = builder.buildLocatorForElement(target);

        assertEquals(SmartLocatorResult.RiskLevel.HIGH, result.getSelectedRiskLevel());
        assertTrue(reasonContains(result, "fallback"));
        assertFalse(result.getCandidates().isEmpty());
        assertTrue(result.getCandidates().stream().allMatch(candidate -> candidate.getRiskLevel() != null));
        assertTrue(result.getCandidates().stream().anyMatch(candidate ->
            !candidate.isUnique() && candidate.getReason().contains("non-unique")));
    }

    @Test
    void candidateListIsRankedAndExplainable() {
        driver.get(htmlUrl("""
            <form class="form-card">
              <span class="e-title">Email</span>
              <input id="email_new" name="userEmail" data-testid="email-field" type="email" aria-label="Primary email">
            </form>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.id("email_new")));

        assertFalse(result.getSelectedLocator().isBlank());
        assertFalse(result.getSelectedReason().isBlank());
        assertNotNull(result.getSelectedRiskLevel());
        assertFalse(result.getCandidates().isEmpty());
        for (int index = 0; index < result.getCandidates().size(); index++) {
            SmartLocatorResult.LocatorCandidate candidate = result.getCandidates().get(index);
            assertEquals(index + 1, candidate.getRank());
            assertFalse(candidate.getValue().isBlank());
            assertFalse(candidate.getReason().isBlank());
            assertNotNull(candidate.getRiskLevel());
        }
    }

    @Test
    void quoteEscapingKeepsGeneratedLocatorValid() {
        driver.get(htmlUrl("""
            <button aria-label="Save user's profile"></button>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("button")));

        assertDoesNotThrow(() -> driver.findElements(byFor(result)));
        assertFalse(driver.findElements(byFor(result)).isEmpty());
    }

    @Test
    void doubleQuoteEscapingKeepsGeneratedLocatorValid() {
        driver.get(htmlUrl("""
            <button aria-label='Save "admin" profile'></button>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector("button")));

        assertSelected(result, "css", "[aria-label='Save \"admin\" profile']");
        assertDoesNotThrow(() -> driver.findElements(byFor(result)));
        assertFalse(driver.findElements(byFor(result)).isEmpty());
    }

    @Test
    void hiddenDuplicateKeepsSharedSelectorUnsafeUntilVisibilityFilteringExists() {
        driver.get(htmlUrl("""
            <input aria-label="Email" hidden>
            <input aria-label="Email">
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElements(By.cssSelector("[aria-label='Email']")).get(1));
        SmartLocatorResult.LocatorCandidate ariaCandidate = candidateOrNull(result, "[aria-label='Email']");

        assertNotNull(ariaCandidate);
        assertFalse(ariaCandidate.isUnique());
        assertEquals(SmartLocatorResult.RiskLevel.HIGH, ariaCandidate.getRiskLevel());
        assertFalse("[aria-label='Email']".equals(result.getLocator())
            && result.getSelectedRiskLevel() != SmartLocatorResult.RiskLevel.HIGH);
    }

    @Test
    void noGoodAttributesUseHighRiskFallback() {
        driver.get(htmlUrl("""
            <div class="unknown-control"></div>
            """));

        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector(".unknown-control")));

        assertEquals(SmartLocatorResult.RiskLevel.HIGH, result.getSelectedRiskLevel());
        assertTrue(reasonContains(result, "fallback"));
        assertFalse(result.getLocator().isBlank());
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
        List<WebElement> matches = driver.findElements(byFor(result));
        assertEquals(1, matches.size(), "locator must be unique");
    }

    private static By byFor(SmartLocatorResult result) {
        return "xpath".equals(result.getLocatorType())
            ? By.xpath(result.getLocator())
            : By.cssSelector(result.getLocator());
    }

    private static void assertSelected(SmartLocatorResult result, String type, String locator) {
        assertEquals(type, result.getLocatorType());
        assertEquals(locator, result.getLocator());
    }

    private static void assertCandidate(SmartLocatorResult result, String type, String locator) {
        assertTrue(result.getCandidates().stream().anyMatch(candidate ->
            type.equals(candidate.getType()) && locator.equals(candidate.getValue())),
            "expected candidate " + type + "=" + locator + " in " + result.getCandidates());
    }

    private void assertSelectedLocatorForHtml(String body, String targetSelector, String expectedType,
                                              String expectedLocator, SmartLocatorResult.RiskLevel expectedRisk) {
        driver.get(htmlUrl(body));
        SmartLocatorResult result = builder.buildLocatorForElement(driver.findElement(By.cssSelector(targetSelector)));

        assertSelected(result, expectedType, expectedLocator);
        assertEquals(expectedRisk, result.getSelectedRiskLevel());
        assertUnique(result, driver);
    }

    private static int candidateRank(SmartLocatorResult result, String locator) {
        return result.getCandidates().stream()
            .filter(candidate -> locator.equals(candidate.getValue()))
            .mapToInt(SmartLocatorResult.LocatorCandidate::getRank)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing candidate " + locator));
    }

    private static boolean reasonContains(SmartLocatorResult result, String expected) {
        return result.getSelectedReason().toLowerCase().contains(expected.toLowerCase());
    }

    private static void assertGeneratedIdCandidateIsHighRiskIfPresent(SmartLocatorResult result, String locator) {
        SmartLocatorResult.LocatorCandidate candidate = candidateOrNull(result, locator);
        if (candidate == null) {
            return;
        }
        assertEquals(SmartLocatorResult.RiskLevel.HIGH, candidate.getRiskLevel());
        assertTrue(candidateReasonContains(candidate, "generated")
            || candidateReasonContains(candidate, "unstable"));
    }

    private static SmartLocatorResult.LocatorCandidate candidateOrNull(SmartLocatorResult result, String locator) {
        return result.getCandidates().stream()
            .filter(candidate -> locator.equals(candidate.getValue()))
            .findFirst()
            .orElse(null);
    }

    private static boolean candidateReasonContains(SmartLocatorResult.LocatorCandidate candidate, String expected) {
        return candidate.getReason().toLowerCase().contains(expected.toLowerCase());
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
