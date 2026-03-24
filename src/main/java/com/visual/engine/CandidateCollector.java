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
    public List<CollectedElementMetadata> collect(WebDriver driver) {
        List<CollectedElementMetadata> collected = new ArrayList<>();
        Object raw = ((JavascriptExecutor) driver).executeScript(BrowserSemanticScripts.visualCandidateCollectionScript());
        if (raw instanceof List<?> entries) {
            for (Object entry : entries) {
                collected.add(toMetadata(entry));
            }
        }
        return collected;
    }

    public CollectedElementMetadata metadata(WebDriver driver, WebElement element) {
        Object raw = ((JavascriptExecutor) driver)
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

    private static CollectedElementMetadata toMetadata(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new CollectedElementMetadata(0, 0, 0, 0, "", "", "", "", "", "", "");
        }
        return new CollectedElementMetadata(
            toInt(map.get("x")),
            toInt(map.get("y")),
            toInt(map.get("w")),
            toInt(map.get("h")),
            toStringValue(map.get("text")),
            toStringValue(map.get("selector")),
            toStringValue(map.get("kind")),
            toStringValue(map.get("tag")),
            toStringValue(map.get("accessibleName")),
            toStringValue(map.get("semanticRole")),
            toStringValue(map.get("autocomplete"))
        );
    }
}
