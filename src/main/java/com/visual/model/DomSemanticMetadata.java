package com.visual.model;

public final class DomSemanticMetadata {
    public final String accessibleName;
    public final String semanticRole;
    public final String autocomplete;
    public final String labelText;
    public final String placeholder;
    public final String descriptionText;
    public final String sectionContext;
    public final String parentContext;
    public final String inputType;

    public DomSemanticMetadata(String accessibleName, String semanticRole, String autocomplete, String labelText,
                               String placeholder, String descriptionText, String sectionContext,
                               String parentContext, String inputType) {
        this.accessibleName = accessibleName;
        this.semanticRole = semanticRole;
        this.autocomplete = autocomplete;
        this.labelText = labelText;
        this.placeholder = placeholder;
        this.descriptionText = descriptionText;
        this.sectionContext = sectionContext;
        this.parentContext = parentContext;
        this.inputType = inputType;
    }
}
