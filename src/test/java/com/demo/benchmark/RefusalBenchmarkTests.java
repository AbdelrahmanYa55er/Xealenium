package com.demo.benchmark;

import com.epam.healenium.SelfHealingDriver;
import com.visual.driver.VisualDriver;
import com.visual.engine.VisualHealingEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RefusalBenchmarkTests extends BenchmarkPageSupport {
    private ChromeDriver chrome;
    private SelfHealingDriver healingDriver;
    private VisualDriver visualDriver;

    @Test
    void xealeniumRefusesWhenNoComparableControlExists() {
        BenchmarkScenarioCatalog.printSummaryOnce();
        setUpDrivers();
        primeBaseline();

        visualDriver.getEngine().setCaptureBaseline(false);
        visualDriver.getEngine().setRefreshBaseline(false);
        visualDriver.get(pageUrl("updated_refusal.html"));

        By locator = By.id("project-code");
        assertThrows(NoSuchElementException.class, () -> chrome.findElement(locator));
        assertThrows(NoSuchElementException.class, () -> healingDriver.findElement(locator));
        assertThrows(NoSuchElementException.class, () -> visualDriver.findElement(locator));
    }

    private void setUpDrivers() {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--remote-allow-origins=*", "--no-sandbox", "--start-maximized");
        chrome = new ChromeDriver(opts);
        healingDriver = SelfHealingDriver.create(chrome);
        visualDriver = new VisualDriver(healingDriver, chrome);
        visualDriver.getEngine().setInteractiveMode(false);
        VisualHealingEngine.REPORTS.clear();
    }

    private void primeBaseline() {
        visualDriver.getEngine().setCaptureBaseline(true);
        visualDriver.getEngine().setRefreshBaseline(true);
        visualDriver.get(pageUrl("baseline_refusal.html"));
        visualDriver.findElement(By.id("project-code"));
        visualDriver.findElement(By.id("approve-btn"));
    }

    @AfterEach
    void tearDown() {
        if (visualDriver != null) {
            visualDriver.quit();
        } else if (healingDriver != null) {
            healingDriver.quit();
        } else if (chrome != null) {
            chrome.quit();
        }
        visualDriver = null;
        healingDriver = null;
        chrome = null;
    }
}
