package com.demo.automationexercise.pages;

import com.demo.automationexercise.AutomationExercisePageSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class SignupLoginPage extends AutomationExercisePageSupport {
    public static final By SIGNUP_NAME = By.cssSelector("input[data-qa='signup-name']");
    public static final By SIGNUP_EMAIL = By.cssSelector("input[data-qa='signup-email']");
    public static final By SIGNUP_BUTTON = By.cssSelector("button[data-qa='signup-button']");
    public static final By LOGIN_EMAIL = By.cssSelector("input[data-qa='login-email']");
    public static final By LOGIN_PASSWORD = By.cssSelector("input[data-qa='login-password']");
    public static final By LOGIN_BUTTON = By.cssSelector("button[data-qa='login-button']");

    public SignupLoginPage(WebDriver driver) {
        super(driver);
    }

    public void signup(String name, String email) {
        type(SIGNUP_NAME, name);
        type(SIGNUP_EMAIL, email);
        click(SIGNUP_BUTTON);
    }

    public void login(String email, String password) {
        type(LOGIN_EMAIL, email);
        type(LOGIN_PASSWORD, password);
        click(LOGIN_BUTTON);
    }
}
