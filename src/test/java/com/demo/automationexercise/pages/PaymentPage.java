package com.demo.automationexercise.pages;

import com.demo.automationexercise.AutomationExercisePageSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class PaymentPage extends AutomationExercisePageSupport {
    public static final By NAME_ON_CARD = By.cssSelector("input[data-qa='name-on-card']");
    public static final By CARD_NUMBER = By.cssSelector("input[data-qa='card-number']");
    public static final By CVC = By.cssSelector("input[data-qa='cvc']");
    public static final By EXPIRY_MONTH = By.cssSelector("input[data-qa='expiry-month']");
    public static final By EXPIRY_YEAR = By.cssSelector("input[data-qa='expiry-year']");
    public static final By PAY_BUTTON = By.cssSelector("button[data-qa='pay-button']");

    public PaymentPage(WebDriver driver) {
        super(driver);
    }

    public void fillPaymentDetails(String name, String cardNumber, String cvc, String month, String year) {
        type(NAME_ON_CARD, name);
        type(CARD_NUMBER, cardNumber);
        type(CVC, cvc);
        type(EXPIRY_MONTH, month);
        type(EXPIRY_YEAR, year);
    }

    public void payAndConfirm() {
        click(PAY_BUTTON);
    }
}
