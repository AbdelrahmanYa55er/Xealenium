package com.demo.benchmark;

import com.epam.healenium.SelfHealingDriver;
import com.visual.driver.VisualDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VisualPageMatrixTests extends BenchmarkPageSupport {
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
        BenchmarkScenarioCatalog.printSummaryOnce();
        setUpDriver();
        driver.getEngine().setInteractiveMode(false);
        driver.getEngine().setCaptureBaseline(true);
        driver.getEngine().setRefreshBaseline(true);
        fillForm(pageUrl("baseline.html"));

        driver.getEngine().setCaptureBaseline(false);
        driver.getEngine().setRefreshBaseline(false);
        fillForm(pageUrl(targetPage));
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
        WebElement fname = driver.findElement(By.id("fname"));
        assertSemanticTarget(driver, "fname", fname, "first name", "given name", "forename", "fname");
        clearIfSupported(fname);
        fname.sendKeys("Visual");
        assertNotNull(fname);
        slow();

        WebElement lname = driver.findElement(By.id("lname"));
        assertSemanticTarget(driver, "lname", lname, "last name", "family name", "surname", "lname");
        clearIfSupported(lname);
        lname.sendKeys("Engine");
        assertNotNull(lname);
        slow();

        WebElement email = driver.findElement(By.id("email"));
        assertSemanticTarget(driver, "email", email, "email", "email address", "mail contact");
        clearIfSupported(email);
        email.sendKeys("visual@heal.io");
        assertNotNull(email);
        slow();

        WebElement phone = driver.findElement(By.id("phone"));
        assertSemanticTarget(driver, "phone", phone, "phone", "phone line", "telephone", "cell");
        clearIfSupported(phone);
        phone.sendKeys("555-VIZ");
        assertNotNull(phone);
        slow();

        WebElement city = driver.findElement(By.id("city"));
        assertSemanticTarget(driver, "city", city, "city", "city name", "town", "municipality");
        clearIfSupported(city);
        city.sendKeys("AI City");
        assertNotNull(city);
        slow();

        WebElement zip = driver.findElement(By.id("zip"));
        assertSemanticTarget(driver, "zip", zip, "zip", "zip code", "postal", "postal code", "postcode");
        clearIfSupported(zip);
        zip.sendKeys("00001");
        assertNotNull(zip);
        slow();

        WebElement country = driver.findElement(By.id("country"));
        assertSemanticTarget(driver, "country", country, "country", "nation", "location", "region");
        country.click();
        assertNotNull(country);
        slow();

        WebElement terms = driver.findElement(By.id("terms"));
        assertSemanticTarget(driver, "terms", terms, "terms", "agreement", "policy");
        terms.click();
        assertNotNull(terms);
        slow();

        WebElement newsletter = driver.findElement(By.id("newsletter"));
        assertSemanticTarget(driver, "newsletter", newsletter, "newsletter", "email feed", "mailing list", "updates");
        newsletter.click();
        assertNotNull(newsletter);
        slow();

        WebElement submitBtn = driver.findElement(By.id("submit-btn"));
        assertSemanticTarget(driver, "submit-btn", submitBtn, "register", "submit", "finish");
        submitBtn.click();
        assertNotNull(submitBtn);

    }

    private void slow() {
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

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
        driver = null;
        chrome = null;
    }
}
