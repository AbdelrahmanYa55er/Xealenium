package com.demo.automationexercise.pages;

import com.demo.automationexercise.AutomationExercisePageSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class CartPage extends AutomationExercisePageSupport {
    public static final By BLUE_TOP_CART_ITEM = By.cssSelector("#product-1 .cart_description a[href='/product_details/1']");
    public static final By CHECKOUT_BUTTON = By.cssSelector("a.check_out[href='/checkout']");

    public CartPage(WebDriver driver) {
        super(driver);
    }

    public void assertBlueTopInCart() {
        assertVisible(BLUE_TOP_CART_ITEM, "Blue Top cart item");
    }

    public void proceedToCheckout() {
        click(CHECKOUT_BUTTON);
    }
}
