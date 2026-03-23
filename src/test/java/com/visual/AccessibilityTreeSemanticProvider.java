package com.visual;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AccessibilityTreeSemanticProvider implements SemanticProvider {
    private static final String PROBE_ATTRIBUTE = "data-visual-ax-probe";

    @Override
    @SuppressWarnings("unchecked")
    public SemanticSignals extract(WebDriver driver, WebElement element) {
        if (!(driver instanceof JavascriptExecutor js) || element == null) {
            return SemanticSignals.empty("ax-unavailable");
        }

        Method cdpMethod = resolveCdpMethod(driver);
        if (cdpMethod == null) {
            return SemanticSignals.empty("ax-unavailable");
        }

        String probe = "ax-" + UUID.randomUUID();
        try {
            js.executeScript("arguments[0].setAttribute(arguments[1], arguments[2]);", element, PROBE_ATTRIBUTE, probe);

            Map<String, Object> document = executeCdp(driver, cdpMethod, "DOM.getDocument", Map.of());
            int rootNodeId = nestedInt(document, "root", "nodeId");
            if (rootNodeId <= 0) {
                return SemanticSignals.empty("ax");
            }

            Map<String, Object> query = executeCdp(driver, cdpMethod, "DOM.querySelector",
                Map.of("nodeId", rootNodeId, "selector", "[" + PROBE_ATTRIBUTE + "=\"" + probe + "\"]"));
            int nodeId = toInt(query.get("nodeId"));
            if (nodeId <= 0) {
                return SemanticSignals.empty("ax");
            }

            Map<String, Object> partialTree = executeCdp(driver, cdpMethod, "Accessibility.getPartialAXTree",
                Map.of("nodeId", nodeId, "fetchRelatives", false));
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) partialTree.get("nodes");
            if (nodes == null || nodes.isEmpty()) {
                return SemanticSignals.empty("ax");
            }

            Map<String, Object> node = nodes.get(0);
            String accessibleName = propertyValue(node.get("name"));
            String role = propertyValue(node.get("role")).toLowerCase();

            return new SemanticSignals(accessibleName, role, "", "ax");
        } catch (Exception ignored) {
            return SemanticSignals.empty("ax-failed");
        } finally {
            try {
                js.executeScript("arguments[0].removeAttribute(arguments[1]);", element, PROBE_ATTRIBUTE);
            } catch (Exception ignored) {
            }
        }
    }

    private static Method resolveCdpMethod(WebDriver driver) {
        try {
            return driver.getClass().getMethod("executeCdpCommand", String.class, Map.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> executeCdp(WebDriver driver, Method method, String command, Map<String, Object> params) throws Exception {
        Object result = method.invoke(driver, command, params);
        return result instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static int nestedInt(Map<String, Object> payload, String parentKey, String childKey) {
        Object parent = payload.get(parentKey);
        if (!(parent instanceof Map<?, ?> map)) return 0;
        return toInt(((Map<String, Object>) map).get(childKey));
    }

    @SuppressWarnings("unchecked")
    private static String propertyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object inner = ((Map<String, Object>) map).get("value");
            return inner == null ? "" : String.valueOf(inner);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
