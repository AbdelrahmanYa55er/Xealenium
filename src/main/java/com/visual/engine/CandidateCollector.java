package com.visual.engine;

import com.visual.model.CollectedElementMetadata;
import com.visual.semantic.BrowserSemanticScripts;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CandidateCollector {
    @SuppressWarnings("unchecked")
    public List<CollectedElementMetadata> collect(WebDriver driver) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) ((JavascriptExecutor) driver)
            .executeScript(BrowserSemanticScripts.visualCandidateCollectionScript());
        List<CollectedElementMetadata> collected = new ArrayList<>();
        for (Map<String, Object> entry : raw) {
            collected.add(toMetadata(entry));
        }
        return collected;
    }

    @SuppressWarnings("unchecked")
    public CollectedElementMetadata metadata(WebDriver driver, WebElement element) {
        Map<String, Object> raw = (Map<String, Object>) ((JavascriptExecutor) driver)
            .executeScript(BrowserSemanticScripts.visualMetadataScript(), element);
        return toMetadata(raw);
    }

    public WebElement resolveCandidateElement(WebDriver driver, int candidateIndex) {
        try {
            Object raw = ((JavascriptExecutor) driver).executeScript(
                "return window.__visualCandidates && window.__visualCandidates.length > arguments[0] ? window.__visualCandidates[arguments[0]] : null;",
                candidateIndex);
            return raw instanceof WebElement element ? element : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public int[] currentScrollOffset(WebDriver driver) {
        try {
            Object raw = ((JavascriptExecutor) driver).executeScript(
                "return [" +
                    "Math.round(window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0)," +
                    "Math.round(window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0)" +
                "];");
            if (raw instanceof List<?> values && values.size() >= 2) {
                return new int[]{toInt(values.get(0)), toInt(values.get(1))};
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String toStringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static CollectedElementMetadata toMetadata(Map<String, Object> raw) {
        return new CollectedElementMetadata(
            toInt(raw.get("x")),
            toInt(raw.get("y")),
            toInt(raw.get("w")),
            toInt(raw.get("h")),
            toStringValue(raw.get("text")),
            toStringValue(raw.get("selector")),
            toStringValue(raw.get("kind")),
            toStringValue(raw.get("tag")),
            toStringValue(raw.get("accessibleName")),
            toStringValue(raw.get("semanticRole")),
            toStringValue(raw.get("autocomplete"))
        );
    }
}
