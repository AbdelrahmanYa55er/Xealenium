package com.visual.semantic;

import com.visual.model.DomSemanticMetadata;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

public class DomSemanticProvider implements SemanticProvider {
    @Override
    public SemanticSignals extract(WebDriver driver, WebElement element) {
        if (!(driver instanceof JavascriptExecutor js) || element == null) {
            return SemanticSignals.empty("dom-unavailable");
        }

        DomSemanticMetadata meta = extractMetadata(js, element);
        if (meta == null) {
            return SemanticSignals.empty("dom");
        }

        return new SemanticSignals(
            meta.accessibleName,
            meta.semanticRole,
            meta.autocomplete,
            meta.labelText,
            meta.placeholder,
            meta.descriptionText,
            meta.sectionContext,
            meta.parentContext,
            meta.inputType,
            "dom"
        );
    }

    private static DomSemanticMetadata extractMetadata(JavascriptExecutor js, WebElement element) {
        Object raw = js.executeScript(BrowserSemanticScripts.domSemanticExtractionScript(), element);
        if (!(raw instanceof Map<?, ?> meta)) {
            return null;
        }
        return new DomSemanticMetadata(
            str(meta.get("accessibleName")),
            str(meta.get("semanticRole")),
            str(meta.get("autocomplete")),
            str(meta.get("labelText")),
            str(meta.get("placeholder")),
            str(meta.get("descriptionText")),
            str(meta.get("sectionContext")),
            str(meta.get("parentContext")),
            str(meta.get("inputType"))
        );
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}


