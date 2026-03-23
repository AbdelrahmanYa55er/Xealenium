package com.visual;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class SemanticSignalExtractor {
    private final SemanticProvider accessibilityProvider;
    private final SemanticProvider domProvider;

    public SemanticSignalExtractor() {
        this(new AccessibilityTreeSemanticProvider(), new DomSemanticProvider());
    }

    public SemanticSignalExtractor(SemanticProvider accessibilityProvider, SemanticProvider domProvider) {
        this.accessibilityProvider = accessibilityProvider;
        this.domProvider = domProvider;
    }

    public SemanticSignals extract(WebDriver driver, WebElement element) {
        SemanticSignals dom = domProvider.extract(driver, element);
        SemanticSignals ax = accessibilityProvider.extract(driver, element);
        String accessibleName = firstNonBlank(ax.getAccessibleName(), dom.getAccessibleName());
        String semanticRole = preferRole(ax.getSemanticRole(), dom.getSemanticRole());
        String autocomplete = firstNonBlank(ax.getAutocomplete(), dom.getAutocomplete());
        String labelText = firstNonBlank(ax.getLabelText(), dom.getLabelText());
        String placeholder = firstNonBlank(ax.getPlaceholder(), dom.getPlaceholder());
        String descriptionText = firstNonBlank(ax.getDescriptionText(), dom.getDescriptionText());
        String sectionContext = firstNonBlank(ax.getSectionContext(), dom.getSectionContext());
        String parentContext = firstNonBlank(ax.getParentContext(), dom.getParentContext());
        String inputType = firstNonBlank(ax.getInputType(), dom.getInputType());
        String source = mergeSource(
            sourceForField(ax.getAccessibleName(), ax.getSemanticRole(), ax.getAutocomplete(),
                ax.getLabelText(), ax.getPlaceholder(), ax.getDescriptionText(), ax.getSectionContext(), ax.getParentContext(), ax.getInputType(),
                accessibleName, semanticRole, autocomplete, labelText, placeholder, descriptionText, sectionContext, parentContext, inputType, "ax"),
            sourceForField(dom.getAccessibleName(), dom.getSemanticRole(), dom.getAutocomplete(),
                dom.getLabelText(), dom.getPlaceholder(), dom.getDescriptionText(), dom.getSectionContext(), dom.getParentContext(), dom.getInputType(),
                accessibleName, semanticRole, autocomplete, labelText, placeholder, descriptionText, sectionContext, parentContext, inputType, "dom")
        );
        return new SemanticSignals(accessibleName, semanticRole, autocomplete,
            labelText, placeholder, descriptionText, sectionContext, parentContext, inputType, source);
    }

    private static String preferRole(String axRole, String domRole) {
        String normalizedAx = canonical(axRole);
        if (normalizedAx.isBlank() || normalizedAx.equals("generic")) {
            return trim(domRole);
        }
        return trim(axRole);
    }

    private static String sourceForField(String sourceAccessible, String sourceRole, String sourceAutocomplete,
                                         String sourceLabelText, String sourcePlaceholder, String sourceDescription,
                                         String sourceSection, String sourceParentContext, String sourceInputType,
                                         String finalAccessible, String finalRole, String finalAutocomplete,
                                         String finalLabelText, String finalPlaceholder, String finalDescription,
                                         String finalSection, String finalParentContext, String finalInputType,
                                         String sourceLabel) {
        if (canonical(sourceAccessible).equals(canonical(finalAccessible))
            || canonical(sourceRole).equals(canonical(finalRole))
            || canonical(sourceAutocomplete).equals(canonical(finalAutocomplete))
            || canonical(sourceLabelText).equals(canonical(finalLabelText))
            || canonical(sourcePlaceholder).equals(canonical(finalPlaceholder))
            || canonical(sourceDescription).equals(canonical(finalDescription))
            || canonical(sourceSection).equals(canonical(finalSection))
            || canonical(sourceParentContext).equals(canonical(finalParentContext))
            || canonical(sourceInputType).equals(canonical(finalInputType))) {
            return sourceLabel;
        }
        return "";
    }

    private static String mergeSource(String left, String right) {
        String a = trim(left);
        String b = trim(right);
        if (a.isBlank()) return b;
        if (b.isBlank() || a.equals(b)) return a;
        return a + "+" + b;
    }

    private static String firstNonBlank(String primary, String fallback) {
        String left = trim(primary);
        return left.isBlank() ? trim(fallback) : left;
    }

    private static String trim(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String canonical(String value) {
        return trim(value).toLowerCase();
    }
}
