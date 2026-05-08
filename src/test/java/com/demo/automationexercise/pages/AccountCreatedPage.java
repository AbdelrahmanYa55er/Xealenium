package com.demo.automationexercise.pages;

import com.demo.automationexercise.AutomationExercisePageSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class AccountCreatedPage extends AutomationExercisePageSupport {
    public static final By HEADING = By.cssSelector("h2[data-qa='account-created']");
    public static final By CONTINUE = By.cssSelector("a[data-qa='continue-button']");

    public AccountCreatedPage(WebDriver driver) {
        super(driver);
    }

    public void assertAccountCreatedVisible() {
        assertVisible(HEADING, "account-created heading");
    }

    public void continueNext() {
        click(CONTINUE);
    }
}
