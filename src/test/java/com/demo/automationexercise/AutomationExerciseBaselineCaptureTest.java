package com.demo.automationexercise;

import com.demo.automationexercise.pages.AccountCreatedPage;
import com.demo.automationexercise.pages.CartPage;
import com.demo.automationexercise.pages.CheckoutPage;
import com.demo.automationexercise.pages.OrderPlacedPage;
import com.demo.automationexercise.pages.PaymentPage;
import com.demo.automationexercise.pages.ProductsPage;
import com.demo.automationexercise.pages.SignupLoginPage;
import org.junit.jupiter.api.Test;

public class AutomationExerciseBaselineCaptureTest extends AutomationExerciseBaseTest {
    @Override
    protected boolean captureBaseline() {
        return true;
    }

    @Override
    protected String flowName() {
        return "BASELINE";
    }

    @Test
    void captureAutomationExerciseBaselineFlow() throws Exception {
        SignupLoginPage signupLoginPage = new SignupLoginPage(driver);
        AccountCreatedPage accountCreatedPage = new AccountCreatedPage(driver);
        ProductsPage productsPage = new ProductsPage(driver);
        CartPage cartPage = new CartPage(driver);
        CheckoutPage checkoutPage = new CheckoutPage(driver);
        PaymentPage paymentPage = new PaymentPage(driver);
        OrderPlacedPage orderPlacedPage = new OrderPlacedPage(driver);

        openBaselinePage("login.html");
        step("Signup with old baseline locators");
        signupLoginPage.signup("Jane Customer", "jane.customer@example.test");

        openBaselinePage("account_created.html");
        step("Assert account created and continue");
        accountCreatedPage.assertAccountCreatedVisible();
        accountCreatedPage.continueNext();

        openBaselinePage("login.html");
        step("Login with old baseline locators");
        signupLoginPage.login("jane.customer@example.test", "Password123!");

        openBaselinePage("products.html");
        step("Assert products and add Blue Top to cart");
        productsPage.assertProductsVisible();
        productsPage.addBlueTopToCart();

        openBaselinePage("cart.html");
        step("Assert Blue Top in cart and proceed to checkout");
        cartPage.assertBlueTopInCart();
        cartPage.proceedToCheckout();

        openBaselinePage("checkout.html");
        step("Assert checkout details and place order");
        checkoutPage.assertCheckoutInfoVisible();
        checkoutPage.placeOrder();

        openBaselinePage("payment.html");
        step("Fill payment details and confirm payment");
        paymentPage.fillPaymentDetails("Jane Customer", "4111111111111111", "311", "12", "2030");
        paymentPage.payAndConfirm();

        openBaselinePage("order_placed.html");
        step("Assert order placed and invoice action");
        orderPlacedPage.assertOrderPlaced();
        orderPlacedPage.downloadInvoiceOrCheckVisible();

        assertBaselineContains(SignupLoginPage.SIGNUP_NAME);
        assertBaselineContains(SignupLoginPage.SIGNUP_EMAIL);
        assertBaselineContains(SignupLoginPage.SIGNUP_BUTTON);
        assertBaselineContains(SignupLoginPage.LOGIN_EMAIL);
        assertBaselineContains(SignupLoginPage.LOGIN_PASSWORD);
        assertBaselineContains(SignupLoginPage.LOGIN_BUTTON);
        assertBaselineContains(AccountCreatedPage.HEADING);
        assertBaselineContains(AccountCreatedPage.CONTINUE);
        assertBaselineContains(ProductsPage.PRODUCTS_HEADING);
        assertBaselineContains(ProductsPage.ADD_BLUE_TOP_TO_CART);
        assertBaselineContains(CartPage.BLUE_TOP_CART_ITEM);
        assertBaselineContains(CartPage.CHECKOUT_BUTTON);
        assertBaselineContains(CheckoutPage.CHECKOUT_INFO);
        assertBaselineContains(CheckoutPage.PLACE_ORDER);
        assertBaselineContains(PaymentPage.NAME_ON_CARD);
        assertBaselineContains(PaymentPage.CARD_NUMBER);
        assertBaselineContains(PaymentPage.CVC);
        assertBaselineContains(PaymentPage.EXPIRY_MONTH);
        assertBaselineContains(PaymentPage.EXPIRY_YEAR);
        assertBaselineContains(PaymentPage.PAY_BUTTON);
        assertBaselineContains(OrderPlacedPage.HEADING);
        assertBaselineContains(OrderPlacedPage.INVOICE);
    }
}
