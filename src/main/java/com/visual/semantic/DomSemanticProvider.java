package com.visual.semantic;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

public class DomSemanticProvider implements SemanticProvider {
    @Override
    @SuppressWarnings("unchecked")
    public SemanticSignals extract(WebDriver driver, WebElement element) {
        if (!(driver instanceof JavascriptExecutor js) || element == null) {
            return SemanticSignals.empty("dom-unavailable");
        }

        Object raw = js.executeScript(BrowserSemanticScripts.domSemanticExtractionScript(), element);

        if (!(raw instanceof Map<?, ?> meta)) {
            return SemanticSignals.empty("dom");
        }

        return new SemanticSignals(
            str(meta.get("accessibleName")),
            str(meta.get("semanticRole")),
            str(meta.get("autocomplete")),
            str(meta.get("labelText")),
            str(meta.get("placeholder")),
            str(meta.get("descriptionText")),
            str(meta.get("sectionContext")),
            str(meta.get("parentContext")),
            str(meta.get("inputType")),
            "dom"
        );
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}


