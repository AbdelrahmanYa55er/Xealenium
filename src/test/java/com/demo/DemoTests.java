package com.demo;
import com.epam.healenium.SelfHealingDriver;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Standard Demo Tests (Healenium Only - No Visual Engine)
 * Always runs with a VISIBLE browser.
 */
public class DemoTests {
    private ChromeDriver chrome;
    private SelfHealingDriver driver;
    private String testUrl;

    @BeforeEach
    public void setUp() {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        chrome = new ChromeDriver(opts);
        driver = SelfHealingDriver.create(chrome);
        testUrl = System.getProperty("testUrl", "file:///c:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/pages/baseline.html");
    }

    private void slow() {}

    @Test
    public void testFullRegistrationFlow() throws Exception {
        driver.get(testUrl);
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
    public void tearDown() { if (driver != null) driver.quit(); }
}
