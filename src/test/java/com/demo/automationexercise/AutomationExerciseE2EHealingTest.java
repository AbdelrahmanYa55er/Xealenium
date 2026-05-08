package com.demo.automationexercise;

import com.demo.automationexercise.pages.AccountCreatedPage;
import com.demo.automationexercise.pages.CartPage;
import com.demo.automationexercise.pages.CheckoutPage;
import com.demo.automationexercise.pages.OrderPlacedPage;
import com.demo.automationexercise.pages.PaymentPage;
import com.demo.automationexercise.pages.ProductsPage;
import com.demo.automationexercise.pages.SignupLoginPage;
import org.junit.jupiter.api.Test;

public class AutomationExerciseE2EHealingTest extends AutomationExerciseBaseTest {
    @Override
    protected boolean captureBaseline() {
        return false;
    }

    @Override
    protected String flowName() {
        return "HEALING";
    }

    @Test
    void runAutomationExerciseHealingFlow() {
        SignupLoginPage signupLoginPage = new SignupLoginPage(driver);
        AccountCreatedPage accountCreatedPage = new AccountCreatedPage(driver);
        ProductsPage productsPage = new ProductsPage(driver);
        CartPage cartPage = new CartPage(driver);
        CheckoutPage checkoutPage = new CheckoutPage(driver);
        PaymentPage paymentPage = new PaymentPage(driver);
        OrderPlacedPage orderPlacedPage = new OrderPlacedPage(driver);

        openUpdatedPage("login.html");
        step("Verify signup locators are drifted, then signup through Xealenium");
        assertNativeLookupFails(SignupLoginPage.SIGNUP_NAME);
        assertNativeLookupFails(SignupLoginPage.SIGNUP_EMAIL);
        assertNativeLookupFails(SignupLoginPage.SIGNUP_BUTTON);
        signupLoginPage.signup("Jane Customer", "jane.customer@example.test");
        assertLatestHealingDidNotSelectGlobalNavigation("signup action");
        assertLatestHealingSelected("signup action", "create account", "create new account");

        openUpdatedPage("account_created.html");
        step("Verify account-created locators are drifted, then continue");
        assertNativeLookupFails(AccountCreatedPage.HEADING);
        assertNativeLookupFails(AccountCreatedPage.CONTINUE);
        accountCreatedPage.assertAccountCreatedVisible();
        accountCreatedPage.continueNext();
        assertLatestHealingDidNotSelectGlobalNavigation("account-created continue action");
        assertLatestHealingSelected("account-created continue action", "proceed", "dashboard");

        openUpdatedPage("login.html");
        step("Verify login locators are drifted, then login through Xealenium");
        assertNativeLookupFails(SignupLoginPage.LOGIN_EMAIL);
        assertNativeLookupFails(SignupLoginPage.LOGIN_PASSWORD);
        assertNativeLookupFails(SignupLoginPage.LOGIN_BUTTON);
        signupLoginPage.login("jane.customer@example.test", "Password123!");
        assertLatestHealingDidNotSelectGlobalNavigation("login action");
        assertLatestHealingSelected("login action", "sign in");

        openUpdatedPage("products.html");
        step("Verify products/add-cart locators are drifted, then add Blue Top");
        assertNativeLookupFails(ProductsPage.PRODUCTS_HEADING);
        assertNativeLookupFails(ProductsPage.ADD_BLUE_TOP_TO_CART);
        productsPage.assertProductsVisible();
        productsPage.addBlueTopToCart();
        assertLatestHealingDidNotSelectGlobalNavigation("add Blue Top to cart action");
        assertLatestHealingSelected("add Blue Top to cart action", "blue top", "data-product-id='1'");

        openUpdatedPage("cart.html");
        step("Verify cart locators are drifted, then proceed to checkout");
        assertNativeLookupFails(CartPage.BLUE_TOP_CART_ITEM);
        assertNativeLookupFails(CartPage.CHECKOUT_BUTTON);
        cartPage.assertBlueTopInCart();
        cartPage.proceedToCheckout();
        assertLatestHealingDidNotSelectGlobalNavigation("cart checkout action");
        assertLatestHealingSelected("cart checkout action", "checkout", "continue checkout");

        openUpdatedPage("checkout.html");
        step("Verify checkout locators are drifted, then place order");
        assertNativeLookupFails(CheckoutPage.CHECKOUT_INFO);
        assertNativeLookupFails(CheckoutPage.PLACE_ORDER);
        checkoutPage.assertCheckoutInfoVisible();
        checkoutPage.placeOrder();
        assertLatestHealingDidNotSelectGlobalNavigation("checkout place-order action");
        assertLatestHealingSelected("checkout place-order action", "confirm order");

        openUpdatedPage("payment.html");
        step("Verify payment locators are drifted, then confirm payment");
        assertNativeLookupFails(PaymentPage.NAME_ON_CARD);
        assertNativeLookupFails(PaymentPage.CARD_NUMBER);
        assertNativeLookupFails(PaymentPage.CVC);
        assertNativeLookupFails(PaymentPage.EXPIRY_MONTH);
        assertNativeLookupFails(PaymentPage.EXPIRY_YEAR);
        assertNativeLookupFails(PaymentPage.PAY_BUTTON);
        paymentPage.fillPaymentDetails("Jane Customer", "4111111111111111", "311", "12", "2030");
        paymentPage.payAndConfirm();
        assertLatestHealingDidNotSelectGlobalNavigation("payment confirm action");
        assertLatestHealingSelected("payment confirm action", "confirm payment", "pay and confirm");

        openUpdatedPage("order_placed.html");
        step("Verify order confirmation locators are drifted, then assert final state");
        assertNativeLookupFails(OrderPlacedPage.HEADING);
        assertNativeLookupFails(OrderPlacedPage.INVOICE);
        assertNativeLookupFails(OrderPlacedPage.CONTINUE);
        orderPlacedPage.assertOrderPlaced();
        orderPlacedPage.downloadInvoiceOrCheckVisible();
        orderPlacedPage.continueNext();
        assertLatestHealingDidNotSelectGlobalNavigation("order continue action");
        assertLatestHealingSelected("order continue action", "continue shopping");

        generateReportNow();
        printHealingSummary();
        assertHealingArtifactsGenerated();
    }
}
