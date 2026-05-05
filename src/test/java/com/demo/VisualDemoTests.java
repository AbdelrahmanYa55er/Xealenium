package com.demo;

import com.epam.healenium.SelfHealingDriver;
import com.visual.config.VisualHealingConfig;
import com.visual.driver.VisualDriver;
import com.visual.engine.VisualHealingEngine;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Visual Self-Healing Demo Tests
 *
 * Always runs with a VISIBLE browser.
 * Supports human-in-the-loop via -Dinteractive=true
 */
public class VisualDemoTests {
    private ChromeDriver chrome;
    private VisualDriver driver;
    private String testUrl;

    @BeforeEach
    public void setUp() {
        ChromeOptions opts = new ChromeOptions();
        // Always visible - no headless!
        opts.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        chrome = new ChromeDriver(opts);
        WebDriver recoveredDriver = createRecoveringDriver(chrome);
        VisualHealingConfig config = VisualHealingConfig.fromSystemProperties();
        driver = new VisualDriver(recoveredDriver, chrome, config);

        testUrl = System.getProperty("testUrl", defaultPageUrl("baseline.html"));
    }

    private WebDriver createRecoveringDriver(ChromeDriver chrome) {
        try {
            return SelfHealingDriver.create(chrome);
        } catch (RuntimeException e) {
            System.out.println("[VISUAL-DEMO] Healenium backend unavailable; continuing with Selenium + visual healing only: " + e.getMessage());
            return chrome;
        }
    }

    private String defaultPageUrl(String pageName) {
        return Path.of(System.getProperty("user.dir"), "pages", pageName).toAbsolutePath().toUri().toString();
    }

    private void slow() {}

    @Test
    public void testFullRegistrationFlow() throws Exception {
        driver.get(testUrl);
        System.out.println("[VISUAL-DEMO] Loaded " + driver.getCurrentUrl() + " title=" + driver.getTitle());
        System.out.println("--- Starting form fill ---");

        WebElement fname = driver.findElement(By.id("fname"));
        fname.sendKeys("Visual");
        assertNotNull(fname);
        slow();

        WebElement lname = driver.findElement(By.id("lname"));
        lname.sendKeys("Engine");
        assertNotNull(lname);
        slow();

        WebElement email = driver.findElement(By.id("email"));
        email.sendKeys("visual@heal.io");
        assertNotNull(email);
        slow();

        WebElement phone = driver.findElement(By.id("phone"));
        phone.sendKeys("555-VIZ");
        assertNotNull(phone);
        slow();

        WebElement city = driver.findElement(By.id("city"));
        city.sendKeys("AI City");
        assertNotNull(city);
        slow();

        WebElement zip = driver.findElement(By.id("zip"));
        zip.sendKeys("00001");
        assertNotNull(zip);
        slow();

        WebElement country = driver.findElement(By.id("country"));
        country.click();
        assertNotNull(country);
        slow();

        WebElement terms = driver.findElement(By.id("terms"));
        terms.click();
        assertNotNull(terms);
        slow();

        WebElement newsletter = driver.findElement(By.id("newsletter"));
        newsletter.click();
        assertNotNull(newsletter);
        slow();

        WebElement submitBtn = driver.findElement(By.id("submit-btn"));
        submitBtn.click();
        assertNotNull(submitBtn);

        System.out.println("--- Form fill complete & submitted ---");
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    @AfterAll
    public static void generateReport() {
        if (Boolean.parseBoolean(System.getProperty("report", "true"))) {
            VisualHealingEngine.generateHtmlReport("visual-healing-report.html");
        }
    }
}
