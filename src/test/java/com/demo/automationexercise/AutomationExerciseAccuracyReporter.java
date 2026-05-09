package com.demo.automationexercise;

import com.demo.automationexercise.pages.AccountCreatedPage;
import com.demo.automationexercise.pages.CartPage;
import com.demo.automationexercise.pages.CheckoutPage;
import com.demo.automationexercise.pages.OrderPlacedPage;
import com.demo.automationexercise.pages.PaymentPage;
import com.demo.automationexercise.pages.ProductsPage;
import com.demo.automationexercise.pages.SignupLoginPage;
import com.visual.report.ReportEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class AutomationExerciseAccuracyReporter {
    private static final List<ExpectedTarget> EXPECTED_TARGETS = List.of(
        target("Signup", SignupLoginPage.SIGNUP_NAME.toString(), "Full Name field", "full name"),
        target("Signup", SignupLoginPage.SIGNUP_EMAIL.toString(), "Registration Email field", "registration email"),
        target("Signup", SignupLoginPage.SIGNUP_BUTTON.toString(), "Create Account action", "create new account", "create account"),
        target("Account created", AccountCreatedPage.HEADING.toString(), "Profile creation complete status", "profile creation complete", "profile created"),
        target("Account created", AccountCreatedPage.CONTINUE.toString(), "Continue to dashboard action", "continue to dashboard", "proceed"),
        target("Login", SignupLoginPage.LOGIN_EMAIL.toString(), "Account Email field", "account email"),
        target("Login", SignupLoginPage.LOGIN_PASSWORD.toString(), "Account Password field", "account password"),
        target("Login", SignupLoginPage.LOGIN_BUTTON.toString(), "Sign In action", "sign in"),
        target("Products", ProductsPage.PRODUCTS_HEADING.toString(), "Catalog heading", "catalog heading", "catalog-title"),
        target("Add product", ProductsPage.ADD_BLUE_TOP_TO_CART.toString(), "Add Blue Top to cart action", "add blue top to cart", "data-product-id='1'"),
        target("Cart", CartPage.BLUE_TOP_CART_ITEM.toString(), "Blue Top cart item", "blue top", "product_details/1"),
        target("Cart", CartPage.CHECKOUT_BUTTON.toString(), "Proceed to checkout action", "proceed to checkout", "continue checkout"),
        target("Checkout", CheckoutPage.CHECKOUT_INFO.toString(), "Order review region", "order review"),
        target("Checkout", CheckoutPage.PLACE_ORDER.toString(), "Confirm order action", "confirm order"),
        target("Payment", PaymentPage.NAME_ON_CARD.toString(), "Cardholder Name field", "cardholder name"),
        target("Payment", PaymentPage.CARD_NUMBER.toString(), "Payment Card Number field", "payment card number"),
        target("Payment", PaymentPage.CVC.toString(), "Security Code field", "security code"),
        target("Payment", PaymentPage.EXPIRY_MONTH.toString(), "Expiration Month field", "expiration month"),
        target("Payment", PaymentPage.EXPIRY_YEAR.toString(), "Expiration Year field", "expiration year"),
        target("Payment", PaymentPage.PAY_BUTTON.toString(), "Confirm Payment action", "confirm payment"),
        target("Order placed", OrderPlacedPage.HEADING.toString(), "Purchase confirmation status", "purchase confirmation", "order confirmed"),
        target("Order placed", OrderPlacedPage.INVOICE.toString(), "Download receipt action", "download receipt", "invoice"),
        target("Order placed", OrderPlacedPage.CONTINUE.toString(), "Continue shopping action", "continue shopping")
    );

    private AutomationExerciseAccuracyReporter() {
    }

    static AccuracySummary write(Path outputPath, List<ReportEntry> entries) {
        AccuracySummary summary = summarize(entries);
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, renderMarkdown(summary, entries));
            System.out.println("[AE-ACCURACY] Generated healing accuracy summary -> " + outputPath);
        } catch (IOException e) {
            throw new AssertionError("failed to write AE healing accuracy summary: " + e.getMessage(), e);
        }
        return summary;
    }

    private static AccuracySummary summarize(List<ReportEntry> entries) {
        int rows = Math.min(entries.size(), EXPECTED_TARGETS.size());
        int passed = 0;
        for (int i = 0; i < rows; i++) {
            if (matches(entries.get(i), EXPECTED_TARGETS.get(i))) {
                passed++;
            }
        }
        return new AccuracySummary(EXPECTED_TARGETS.size(), entries.size(), passed);
    }

    private static String renderMarkdown(AccuracySummary summary, List<ReportEntry> entries) {
        StringBuilder out = new StringBuilder();
        out.append("# Automation Exercise Healing Accuracy Summary\n\n");
        out.append("Generated from ordered `VisualHealingEngine.REPORTS` entries during the local AE competition healing flow.\n\n");
        out.append("- Expected healing events: ").append(summary.expectedCount()).append('\n');
        out.append("- Actual healing events: ").append(summary.actualCount()).append('\n');
        out.append("- Passed target checks: ").append(summary.passedCount()).append('\n');
        out.append("- Accuracy: ").append(String.format(Locale.US, "%.1f%%", summary.accuracyPercent())).append("\n\n");
        out.append("| # | Flow step | Old baseline locator | Expected target | Actual healed locator | Strategy | Score | Result |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        int maxRows = Math.max(entries.size(), EXPECTED_TARGETS.size());
        for (int i = 0; i < maxRows; i++) {
            ExpectedTarget expected = i < EXPECTED_TARGETS.size() ? EXPECTED_TARGETS.get(i) : ExpectedTarget.unexpected();
            ReportEntry entry = i < entries.size() ? entries.get(i) : null;
            boolean passed = entry != null && matches(entry, expected);
            out.append("| ").append(i + 1)
                .append(" | ").append(cell(expected.step()))
                .append(" | ").append(cell(expected.oldLocator()))
                .append(" | ").append(cell(expected.expectedDescription()))
                .append(" | ").append(cell(entry == null ? "MISSING" : entry.newLocator))
                .append(" | ").append(cell(entry == null ? "" : entry.selectorStrategy))
                .append(" | ").append(entry == null ? "" : String.format(Locale.US, "%.3f", entry.score))
                .append(" | ").append(passed ? "PASS" : "FAIL")
                .append(" |\n");
        }
        return out.toString();
    }

    private static boolean matches(ReportEntry entry, ExpectedTarget expected) {
        if (!expected.oldLocator().equals(entry.originalLocator)) {
            return false;
        }
        if (!entry.accepted) {
            return false;
        }
        String haystack = normalize(entry.newLocator + " " + entry.accessibleName + " " + entry.selectorStrategy);
        for (String fragment : expected.expectedFragments()) {
            if (haystack.contains(normalize(fragment))) {
                return true;
            }
        }
        return false;
    }

    private static ExpectedTarget target(String step, String locator, String description, String... fragments) {
        return new ExpectedTarget(step, locator, description, List.of(fragments));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("\\\"", "'").trim();
    }

    private static String cell(String value) {
        return (value == null ? "" : value).replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    record AccuracySummary(int expectedCount, int actualCount, int passedCount) {
        double accuracyPercent() {
            return expectedCount == 0 ? 0.0 : (passedCount * 100.0) / expectedCount;
        }

        boolean perfect() {
            return expectedCount == actualCount && expectedCount == passedCount;
        }
    }

    private record ExpectedTarget(String step, String oldLocator, String expectedDescription, List<String> expectedFragments) {
        static ExpectedTarget unexpected() {
            return new ExpectedTarget("Unexpected", "", "No expected healing event", List.of());
        }
    }
}
