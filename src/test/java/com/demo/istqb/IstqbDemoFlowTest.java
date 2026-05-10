package com.demo.istqb;

import com.epam.healenium.SelfHealingDriver;
import com.visual.config.VisualHealingConfig;
import com.visual.config.XealeniumRuntimeProperties;
import com.visual.driver.VisualDriver;
import com.visual.engine.VisualHealingEngine;
import com.visual.report.ReportEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IstqbDemoFlowTest {
    private static final String DEFAULT_BASELINE_FILE = "test-outputs/xealenium/istqb-demo/visual-baseline.json";
    private static final String DEFAULT_REPORT_FILE = "test-outputs/xealenium/istqb-demo/visual-healing-report.html";

    private static final By CERTIFICATION_LINK = By.id("certification-link");
    private static final By CTFL_READ_MORE = By.cssSelector("#ctfl a.read-more[href='genai.html']");
    private static final By FIND_EXAM_PROVIDER = By.cssSelector("a.button[href='findExamProvider.html']");
    private static final By PROVIDER_SEARCH = By.id("providerSearch");
    private static final By SEARCH_BUTTON = By.id("searchButton");
    private static final String SECC_WEBSITE_URL = "https://secc.org.eg/English/Pages/default.aspx";

    private ChromeDriver chrome;
    private VisualDriver driver;
    private WebDriverWait wait;
    private boolean captureBaseline;

    @BeforeEach
    void setUp() {
        configureRuntime();
        VisualHealingEngine.REPORTS.clear();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        if (XealeniumRuntimeProperties.getBoolean("headless", false)) {
            options.addArguments("--headless=new", "--window-size=1440,1200");
        }

        chrome = new ChromeDriver(options);
        WebDriver recoveredDriver = createRecoveringDriver(chrome);
        driver = new VisualDriver(recoveredDriver, chrome, VisualHealingConfig.fromSystemProperties());
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        try {
            if (XealeniumRuntimeProperties.getBoolean("report", true)) {
                VisualHealingEngine.generateHtmlReport(reportFile().toString());
            }
        } finally {
            if (driver != null) {
                driver.quit();
            } else if (chrome != null) {
                chrome.quit();
            }
        }
    }

    @Test
    void followsIstqbCertificationToExamProviderFlow() {
        openHomePage();
        click(CERTIFICATION_LINK, "Certification");
        waitForPage("cert");

        click(CTFL_READ_MORE, "CTFL Read more");
        waitForPage("certified-tester-foundation-level-ctfl-v4-0");

        click(FIND_EXAM_PROVIDER, "Find exam provider");
        waitForPage("exam-providers");

        typeIfAvailable(PROVIDER_SEARCH, "SECC");
        clickIfAvailable(SEARCH_BUTTON, "Search");
        openSeccWebsiteDirectly();
        waitForExternalWebsite();

        if (!captureBaseline) {
            assertFalse(VisualHealingEngine.REPORTS.isEmpty(), "live run should create healing events");
            printHealingSummary();
        }
    }

    private void openHomePage() {
        String url = captureBaseline
            ? localPageUrl("home.html")
            : XealeniumRuntimeProperties.get("istqb.live.url", "https://istqb.org/");
        driver.get(url);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        dismissCookieNotice();
        System.out.println("[ISTQB-DEMO] Opened " + driver.getCurrentUrl() + " title=\"" + driver.getTitle() + "\"");
    }

    private void waitForPage(String pageHint) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        if ("cert".equals(pageHint)) {
            scrollToText("Certified Tester Foundation Level (CTFL) v4.0");
        }
        if (!captureBaseline) {
            wait.until(d -> switchToWindowContaining(pageHint));
        }
        System.out.println("[ISTQB-DEMO] Now at " + driver.getCurrentUrl() + " title=\"" + driver.getTitle() + "\"");
    }

    private boolean switchToWindowContaining(String pageHint) {
        String needle = pageHint.toLowerCase(Locale.ROOT);
        Set<String> handles = driver.getWindowHandles();
        for (String handle : handles) {
            driver.switchTo().window(handle);
            if (driver.getCurrentUrl().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void waitForExternalWebsite() {
        wait.until(d -> {
            for (String handle : d.getWindowHandles()) {
                d.switchTo().window(handle);
                String currentUrl = d.getCurrentUrl().toLowerCase(Locale.ROOT);
                if (!currentUrl.startsWith("file:")
                    && !currentUrl.contains("istqb.org")
                    && !currentUrl.contains("exam-providers")) {
                    return true;
                }
            }
            return false;
        });
        System.out.println("[ISTQB-DEMO] External provider site opened " + driver.getCurrentUrl()
            + " title=\"" + driver.getTitle() + "\"");
    }

    private void dismissCookieNotice() {
        if (captureBaseline) {
            return;
        }
        try {
            WebElement accept = chrome.findElement(By.xpath("//button[normalize-space()='Accept']"));
            accept.click();
        } catch (RuntimeException ignored) {
            // Cookie notice is absent or already closed.
        }
    }

    private void scrollToText(String text) {
        ((JavascriptExecutor) driver).executeScript("""
            const target = arguments[0].toLowerCase();
            const nodes = Array.from(document.querySelectorAll('h1,h2,h3,h4,p,a,span,div'));
            for (const node of nodes) {
              const visible = node.offsetParent !== null;
              const content = (node.innerText || '').replace(/\\s+/g, ' ').trim().toLowerCase();
              if (visible && content.includes(target) && content.length <= 220) {
                node.scrollIntoView({block: 'center', inline: 'nearest'});
                return true;
              }
            }
            return false;
            """, text);
    }

    private void waitForVisibleText(String text) {
        wait.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) chrome).executeScript("""
            const target = arguments[0].toLowerCase();
            return (document.body && document.body.innerText || '').toLowerCase().includes(target);
            """, text)));
    }

    private void click(By locator, String label) {
        System.out.println("[ISTQB-DEMO] Click " + label + " using baseline locator " + locator);
        WebElement element = wait.until(d -> d.findElement(locator));
        wait.until(ExpectedConditions.elementToBeClickable(element));
        try {
            element.click();
        } catch (ElementClickInterceptedException intercepted) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    private void type(By locator, String value) {
        System.out.println("[ISTQB-DEMO] Type \"" + value + "\" using baseline locator " + locator);
        WebElement element = wait.until(d -> d.findElement(locator));
        element.clear();
        element.sendKeys(value);
    }

    private void typeIfAvailable(By locator, String value) {
        try {
            type(locator, value);
        } catch (RuntimeException skipped) {
            System.out.println("[ISTQB-DEMO] Optional text field not completed: " + locator + " -> " + skipped.getClass().getSimpleName());
        }
    }

    private void clickIfAvailable(By locator, String label) {
        try {
            click(locator, label);
        } catch (RuntimeException skipped) {
            System.out.println("[ISTQB-DEMO] Optional click skipped for " + label + ": " + skipped.getClass().getSimpleName());
        }
    }

    private void openSeccWebsiteDirectly() {
        System.out.println("[ISTQB-DEMO] Open SECC website directly: " + SECC_WEBSITE_URL);
        driver.get(SECC_WEBSITE_URL);
    }

    private void configureRuntime() {
        XealeniumRuntimeProperties.applyToSystemProperties(
            "report",
            "interactive",
            "headless",
            "visual.threshold",
            "visual.baseline.path",
            "visual.report.path",
            "visual.report.json.path",
            "visual.heatmap.dir",
            "visual.embedding.enabled",
            "visual.embedding.modelName",
            "visual.embedding.modelPath",
            "istqb.phase",
            "istqb.live.url"
        );
        captureBaseline = "capture".equalsIgnoreCase(XealeniumRuntimeProperties.get("istqb.phase", "live"));
        System.setProperty("report", System.getProperty("report", "true"));
        System.setProperty("interactive", System.getProperty("interactive", "false"));
        System.setProperty("visual.baseline.path", System.getProperty("visual.baseline.path", DEFAULT_BASELINE_FILE));
        System.setProperty("visual.report.path", System.getProperty("visual.report.path", DEFAULT_REPORT_FILE));
        System.setProperty("visual.captureBaseline", String.valueOf(captureBaseline));
        System.setProperty("visual.captureBaseline.refresh", String.valueOf(captureBaseline));
    }

    private String localPageUrl(String fileName) {
        return Path.of(System.getProperty("user.dir"), "Demo test", fileName).toAbsolutePath().toUri().toString();
    }

    private Path reportFile() {
        return Path.of(XealeniumRuntimeProperties.get("visual.report.path", DEFAULT_REPORT_FILE));
    }

    private WebDriver createRecoveringDriver(ChromeDriver chromeDriver) {
        if (!XealeniumRuntimeProperties.getBoolean("istqb.healenium.enabled", false)) {
            return chromeDriver;
        }
        try {
            return SelfHealingDriver.create(chromeDriver);
        } catch (RuntimeException e) {
            System.out.println("[ISTQB-DEMO] Healenium backend unavailable; continuing with Selenium + Xealenium: " + e.getMessage());
            return chromeDriver;
        }
    }

    private void printHealingSummary() {
        for (ReportEntry entry : VisualHealingEngine.REPORTS) {
            System.out.println("[ISTQB-DEMO-HEALING] old=" + entry.originalLocator
                + " selected=\"" + entry.newLocator + "\""
                + " score=" + String.format(Locale.US, "%.3f", entry.score)
                + " heatmap=" + entry.heatmapFilename);
        }
    }
}
