package com.visual.semantic;

import com.visual.model.AxNodeMetadata;

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

            int rootNodeId = nestedInt(executeCdp(driver, cdpMethod, "DOM.getDocument", Map.of()), "root", "nodeId");
            if (rootNodeId <= 0) {
                return SemanticSignals.empty("ax");
            }

            int nodeId = toInt(executeCdp(driver, cdpMethod, "DOM.querySelector",
                Map.of("nodeId", rootNodeId, "selector", "[" + PROBE_ATTRIBUTE + "=\"" + probe + "\"]")).get("nodeId"));
            if (nodeId <= 0) {
                return SemanticSignals.empty("ax");
            }

            AxNodeMetadata metadata = firstAxNodeMetadata(executeCdp(driver, cdpMethod, "Accessibility.getPartialAXTree",
                Map.of("nodeId", nodeId, "fetchRelatives", false)));
            if (metadata == null) {
                return SemanticSignals.empty("ax");
            }
            return new SemanticSignals(metadata.accessibleName, metadata.role, "", "ax");
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

    private static Map<?, ?> executeCdp(WebDriver driver, Method method, String command, Map<String, ?> params) throws Exception {
        Object result = method.invoke(driver, command, params);
        return result instanceof Map<?, ?> map ? map : Map.of();
    }

    private static AxNodeMetadata firstAxNodeMetadata(Map<?, ?> partialTree) {
        Object nodesRaw = partialTree.get("nodes");
        if (!(nodesRaw instanceof List<?> nodes) || nodes.isEmpty()) {
            return null;
        }
        if (!(nodes.get(0) instanceof Map<?, ?> node)) {
            return null;
        }
        return toAxNodeMetadata(node);
    }

    private static AxNodeMetadata toAxNodeMetadata(Map<?, ?> node) {
        return new AxNodeMetadata(
            propertyValue(node.get("name")),
            propertyValue(node.get("role")).toLowerCase()
        );
    }

    private static int nestedInt(Map<?, ?> payload, String parentKey, String childKey) {
        Object parent = payload.get(parentKey);
        if (!(parent instanceof Map<?, ?> map)) return 0;
        return toInt(map.get(childKey));
    }

    private static String propertyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object inner = map.get("value");
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


