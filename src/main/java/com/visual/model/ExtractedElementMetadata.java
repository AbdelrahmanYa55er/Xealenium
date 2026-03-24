package com.visual.model;

import org.openqa.selenium.WebElement;

public final class ExtractedElementMetadata {
    public final WebElement element;
    public final String tagName;
    public final String id;
    public final String name;
    public final String className;
    public final String dataTestId;
    public final String dataTest;
    public final String ariaLabel;
    public final String placeholder;
    public final String accessibleName;
    public final String type;
    public final String text;
    public final String labelText;
    public final String parentText;
    public final String semanticRole;
    public final String autocomplete;
    public final String contentEditable;
    public final String ancestorId;
    public final String ancestorDataTestId;
    public final String ancestorDataTest;
    public final String ancestorClassName;
    public final String ancestorTagName;

    public ExtractedElementMetadata(WebElement element, String tagName, String id, String name, String className,
                                    String dataTestId, String dataTest, String ariaLabel, String placeholder,
                                    String accessibleName, String type, String text, String labelText,
                                    String parentText, String semanticRole, String autocomplete,
                                    String contentEditable, String ancestorId, String ancestorDataTestId,
                                    String ancestorDataTest, String ancestorClassName, String ancestorTagName) {
        this.element = element;
        this.tagName = tagName;
        this.id = id;
        this.name = name;
        this.className = className;
        this.dataTestId = dataTestId;
        this.dataTest = dataTest;
        this.ariaLabel = ariaLabel;
        this.placeholder = placeholder;
        this.accessibleName = accessibleName;
        this.type = type;
        this.text = text;
        this.labelText = labelText;
        this.parentText = parentText;
        this.semanticRole = semanticRole;
        this.autocomplete = autocomplete;
        this.contentEditable = contentEditable;
        this.ancestorId = ancestorId;
        this.ancestorDataTestId = ancestorDataTestId;
        this.ancestorDataTest = ancestorDataTest;
        this.ancestorClassName = ancestorClassName;
        this.ancestorTagName = ancestorTagName;
    }
}
