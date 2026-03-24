package com.visual.semantic;

public class SemanticSignals {
    private final String accessibleName;
    private final String semanticRole;
    private final String autocomplete;
    private final String labelText;
    private final String placeholder;
    private final String descriptionText;
    private final String sectionContext;
    private final String parentContext;
    private final String inputType;
    private final String source;

    public SemanticSignals(String accessibleName, String semanticRole, String autocomplete, String source) {
        this(accessibleName, semanticRole, autocomplete, "", "", "", "", "", "", source);
    }

    public SemanticSignals(String accessibleName, String semanticRole, String autocomplete,
                           String labelText, String placeholder, String descriptionText,
                           String sectionContext, String parentContext, String inputType, String source) {
        this.accessibleName = normalize(accessibleName);
        this.semanticRole = normalize(semanticRole);
        this.autocomplete = normalize(autocomplete);
        this.labelText = normalize(labelText);
        this.placeholder = normalize(placeholder);
        this.descriptionText = normalize(descriptionText);
        this.sectionContext = normalize(sectionContext);
        this.parentContext = normalize(parentContext);
        this.inputType = normalize(inputType);
        this.source = normalize(source);
    }

    public static SemanticSignals empty(String source) {
        return new SemanticSignals("", "", "", "", "", "", "", "", "", source);
    }

    public String getAccessibleName() {
        return accessibleName;
    }

    public String getSemanticRole() {
        return semanticRole;
    }

    public String getAutocomplete() {
        return autocomplete;
    }

    public String getLabelText() {
        return labelText;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public String getSectionContext() {
        return sectionContext;
    }

    public String getParentContext() {
        return parentContext;
    }

    public String getInputType() {
        return inputType;
    }

    public String getSource() {
        return source;
    }

    public SemanticSignals mergeOver(SemanticSignals fallback) {
        if (fallback == null) {
            return this;
        }
        return new SemanticSignals(
            firstNonBlank(accessibleName, fallback.accessibleName),
            firstNonBlank(semanticRole, fallback.semanticRole),
            firstNonBlank(autocomplete, fallback.autocomplete),
            firstNonBlank(labelText, fallback.labelText),
            firstNonBlank(placeholder, fallback.placeholder),
            firstNonBlank(descriptionText, fallback.descriptionText),
            firstNonBlank(sectionContext, fallback.sectionContext),
            firstNonBlank(parentContext, fallback.parentContext),
            firstNonBlank(inputType, fallback.inputType),
            mergeSource(source, fallback.source)
        );
    }

    private static String mergeSource(String primary, String fallback) {
        String left = normalize(primary);
        String right = normalize(fallback);
        if (left.isBlank()) return right;
        if (right.isBlank() || left.equals(right)) return left;
        return left + "+" + right;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return normalize(primary).isBlank() ? normalize(fallback) : normalize(primary);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}


