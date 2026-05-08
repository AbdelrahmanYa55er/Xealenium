package com.demo.automationexercise.pages;

import com.demo.automationexercise.AutomationExercisePageSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class OrderPlacedPage extends AutomationExercisePageSupport {
    public static final By HEADING = By.cssSelector("h2[data-qa='order-placed']");
    public static final By INVOICE = By.cssSelector("a[href^='/download_invoice/']");
    public static final By CONTINUE = By.cssSelector("a[data-qa='continue-button']");

    public OrderPlacedPage(WebDriver driver) {
        super(driver);
    }

    public void assertOrderPlaced() {
        assertVisible(HEADING, "order placed heading");
    }

    public void downloadInvoiceOrCheckVisible() {
        assertVisible(INVOICE, "invoice action");
    }

    public void continueNext() {
        click(CONTINUE);
    }
}
