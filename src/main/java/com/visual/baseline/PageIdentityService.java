package com.visual.baseline;

import com.visual.model.PageIdentity;
import com.visual.semantic.BrowserSemanticScripts;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.Map;

public class PageIdentityService {
    public PageIdentity capture(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor js)) {
            return new PageIdentity("", "");
        }
        Object raw = js.executeScript(BrowserSemanticScripts.pageIdentityScript());
        if (!(raw instanceof Map<?, ?> map)) {
            return new PageIdentity(safeTitle(driver), "");
        }
        return new PageIdentity(
            stringValue(map.get("pageTitle"), safeTitle(driver)),
            stringValue(map.get("pageFingerprint"), "")
        );
    }

    private static String safeTitle(WebDriver driver) {
        try {
            String title = driver.getTitle();
            return title == null ? "" : title;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
