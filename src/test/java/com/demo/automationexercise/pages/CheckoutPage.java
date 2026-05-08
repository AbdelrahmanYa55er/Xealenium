package com.demo.automationexercise.pages;

import com.demo.automationexercise.AutomationExercisePageSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class CheckoutPage extends AutomationExercisePageSupport {
    public static final By CHECKOUT_INFO = By.cssSelector(".checkout-information[data-qa='checkout-info']");
    public static final By PLACE_ORDER = By.cssSelector("a.check_out[href='/payment']");

    public CheckoutPage(WebDriver driver) {
        super(driver);
    }

    public void assertCheckoutInfoVisible() {
        assertVisible(CHECKOUT_INFO, "checkout information");
    }

    public void placeOrder() {
        click(PLACE_ORDER);
    }
}
