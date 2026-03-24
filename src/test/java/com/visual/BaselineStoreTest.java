package com.visual;

import com.visual.baseline.BaselineStore;
import com.visual.model.ElementSnapshot;
import com.visual.model.PageIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BaselineStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void findPrefersFingerprintMatchWhenLocatorExistsOnMultiplePages() {
        BaselineStore store = new BaselineStore(tempDir.resolve("baseline.json").toString());

        ElementSnapshot account = snapshot("file:///forms/account-baseline.html", "By.id: email", "account-mail");
        account.withPageIdentity(new PageIdentity(
            "Account Profile",
            "Account Profile | Email Address | Contact Phone | Billing City",
            "/forms/account-baseline.html",
            "Account Profile | Billing Details",
            "Email Address | Contact Phone | Billing City"
        ));
        ElementSnapshot marketing = snapshot("file:///forms/marketing-baseline.html", "By.id: email", "marketing-mail");
        marketing.withPageIdentity(new PageIdentity(
            "Marketing Signup",
            "Marketing Signup | Email Address | Newsletter Topic | Country",
            "/forms/marketing-baseline.html",
            "Marketing Signup | Stay Updated",
            "Email Address | Newsletter Topic | Country"
        ));

        store.save(account, true);
        store.save(marketing, true);

        ElementSnapshot found = store.find("file:///forms/updated-flow.html", new PageIdentity(
            "Marketing Signup",
            "Marketing Signup | Contact Email | Newsletter Topic | Country",
            "/forms/updated-flow.html",
            "Marketing Signup | Stay Updated",
            "Contact Email | Newsletter Topic | Country"
        ), "By.id: email");

        assertNotNull(found);
        assertEquals("marketing-mail", found.text);
    }

    @Test
    void findFallsBackToUrlSimilarityForLegacySnapshotsWithoutFingerprint() {
        BaselineStore store = new BaselineStore(tempDir.resolve("legacy-baseline.json").toString());

        ElementSnapshot near = snapshot("file:///pages/baseline.html", "By.id: city", "near");
        ElementSnapshot far = snapshot("file:///archive/old-page.html", "By.id: city", "far");

        store.save(near, true);
        store.save(far, true);

        ElementSnapshot found = store.find(
            "file:///pages/updated.html",
            "Updated Page",
            "Complete Profile | Town | Postal | Finish Registration",
            "By.id: city"
        );

        assertNotNull(found);
        assertEquals("near", found.text);
    }

    @Test
    void findPrefersStructuredFormIdentityOverUrlPrefixWhenPagesShareFolder() {
        BaselineStore store = new BaselineStore(tempDir.resolve("structured-baseline.json").toString());

        ElementSnapshot checkout = snapshot("file:///flows/account-checkout-baseline.html", "By.id: city", "checkout-city");
        checkout.withPageIdentity(new PageIdentity(
            "Account Checkout",
            "Account Checkout | Shipping City | Shipping Postal | Continue",
            "/flows/account-checkout-baseline.html",
            "Account Checkout | Shipping",
            "Shipping City | Shipping Postal"
        ));

        ElementSnapshot support = snapshot("file:///flows/account-support-baseline.html", "By.id: city", "support-city");
        support.withPageIdentity(new PageIdentity(
            "Account Support",
            "Account Support | Support City | Ticket Priority | Submit",
            "/flows/account-support-baseline.html",
            "Account Support | Contact Support",
            "Support City | Ticket Priority"
        ));

        store.save(checkout, true);
        store.save(support, true);

        ElementSnapshot found = store.find("file:///flows/account-updated.html", new PageIdentity(
            "Account Support",
            "Account Support | Service City | Ticket Priority | Submit",
            "/flows/account-updated.html",
            "Account Support | Contact Support",
            "Service City | Ticket Priority"
        ), "By.id: city");

        assertNotNull(found);
        assertEquals("support-city", found.text);
    }

    private static ElementSnapshot snapshot(String pageUrl, String locator, String text) {
        return new ElementSnapshot(locator, "", 0, 0, 20, 20, text, pageUrl, "text", "input", "Email", "textbox", "");
    }
}
