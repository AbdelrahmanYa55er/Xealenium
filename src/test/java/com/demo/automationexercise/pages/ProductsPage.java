package com.demo.automationexercise.pages;

import com.demo.automationexercise.AutomationExercisePageSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class ProductsPage extends AutomationExercisePageSupport {
    public static final By PRODUCTS_HEADING = By.xpath("//h2[normalize-space()='All Products']");
    public static final By ADD_BLUE_TOP_TO_CART = By.cssSelector(".productinfo a.add-to-cart[data-product-id='1']");
    public static final By BLUE_TOP_DETAILS = By.cssSelector("a[href='/product_details/1']");

    public ProductsPage(WebDriver driver) {
        super(driver);
    }

    public void assertProductsVisible() {
        assertVisible(PRODUCTS_HEADING, "products heading");
    }

    public void addBlueTopToCart() {
        click(ADD_BLUE_TOP_TO_CART);
    }

    public void openBlueTopDetails() {
        click(BLUE_TOP_DETAILS);
    }
}
