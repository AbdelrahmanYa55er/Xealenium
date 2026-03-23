package com.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EmbeddingFingerprintBuilder {
    private EmbeddingFingerprintBuilder() {
    }

    public static String forSnapshot(String locator, ElementSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return build(locator, snapshot.kind, snapshot.tagName, snapshot.accessibleName, snapshot.semanticRole,
            snapshot.autocomplete, snapshot.labelText, snapshot.placeholder, snapshot.descriptionText,
            snapshot.sectionContext, snapshot.parentContext, snapshot.inputType, snapshot.text);
    }

    public static String build(String locator, String kind, String tagName, String accessibleName, String semanticRole,
                               String autocomplete, String labelText, String placeholder, String descriptionText,
                               String sectionContext, String parentContext, String inputType, String text) {
        List<String> parts = new ArrayList<>();
        append(parts, "locator", normalizeLocator(locator));
        append(parts, "locator_tokens", locatorTokens(locator));
        append(parts, "kind", kind);
        append(parts, "tag", tagName);
        append(parts, "role", semanticRole);
        append(parts, "input_type", inputType);
        append(parts, "accessible_name", accessibleName);
        append(parts, "label_text", labelText);
        append(parts, "placeholder", placeholder);
        append(parts, "description", descriptionText);
        append(parts, "autocomplete", autocomplete);
        append(parts, "section_context", sectionContext);
        append(parts, "parent_context", parentContext);
        append(parts, "text", text);
        append(parts, "field_identity", buildFieldIdentity(accessibleName, labelText, placeholder, autocomplete, inputType, text, locator));
        append(parts, "control_identity", joinSignals(kind, semanticRole, inputType, tagName));
        append(parts, "context_identity", joinSignals(sectionContext, parentContext, descriptionText));
        append(parts, "semantic_summary", semanticSummary(kind, tagName, semanticRole, inputType, accessibleName,
            labelText, placeholder, descriptionText, autocomplete, sectionContext, parentContext, text));
        return String.join("\n", parts);
    }

    public static String buildFieldIdentity(String accessibleName, String labelText, String placeholder,
                                            String autocomplete, String inputType, String text) {
        return buildFieldIdentity(accessibleName, labelText, placeholder, autocomplete, inputType, text, "");
    }

    public static String buildFieldIdentity(String accessibleName, String labelText, String placeholder,
                                            String autocomplete, String inputType, String text, String locator) {
        List<String> parts = new ArrayList<>();
        if (!normalize(accessibleName).isBlank()) {
            parts.add("field " + normalize(accessibleName));
        }
        if (!normalize(labelText).isBlank()) {
            parts.add("label " + normalize(labelText));
        }
        if (!normalize(placeholder).isBlank()) {
            parts.add("placeholder " + normalize(placeholder));
        }
        if (!normalize(autocomplete).isBlank()) {
            parts.add("autocomplete " + normalize(autocomplete));
        }
        if (!normalize(inputType).isBlank()) {
            parts.add("input type " + normalize(inputType));
        }
        if (!parts.isEmpty()) {
            return String.join(". ", parts);
        }
        return joinSignals(locatorTokens(locator), text);
    }

    private static void append(List<String> parts, String label, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            parts.add(label + "=" + normalized);
        }
    }

    private static String normalizeLocator(String locator) {
        String normalized = normalize(locator)
            .replace("by.id:", "id")
            .replace("by.name:", "name")
            .replace("by.cssselector:", "css")
            .replace("by.xpath:", "xpath");
        return normalized.replaceAll("[^a-z0-9:_\\- ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String locatorTokens(String locator) {
        String normalized = locator == null ? "" : locator
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .replaceAll("[^A-Za-z0-9]+", " ")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
        return normalized;
    }

    private static String semanticSummary(String kind, String tagName, String semanticRole, String inputType,
                                          String accessibleName, String labelText, String placeholder,
                                          String descriptionText, String autocomplete, String sectionContext,
                                          String parentContext, String text) {
        List<String> clauses = new ArrayList<>();
        appendSentence(clauses, normalize(kind).isBlank() ? "" : normalize(kind) + " control");
        appendSentence(clauses, normalize(semanticRole).isBlank() ? "" : "role " + normalize(semanticRole));
        appendSentence(clauses, normalize(tagName).isBlank() ? "" : "tag " + normalize(tagName));
        appendSentence(clauses, normalize(inputType).isBlank() ? "" : "input type " + normalize(inputType));
        appendSentence(clauses, normalize(accessibleName).isBlank() ? "" : "named " + normalize(accessibleName));
        appendSentence(clauses, normalize(labelText).isBlank() ? "" : "label " + normalize(labelText));
        appendSentence(clauses, normalize(placeholder).isBlank() ? "" : "placeholder " + normalize(placeholder));
        appendSentence(clauses, normalize(descriptionText).isBlank() ? "" : "description " + normalize(descriptionText));
        appendSentence(clauses, normalize(autocomplete).isBlank() ? "" : "autocomplete " + normalize(autocomplete));
        appendSentence(clauses, normalize(sectionContext).isBlank() ? "" : "section " + normalize(sectionContext));
        appendSentence(clauses, normalize(parentContext).isBlank() ? "" : "group " + normalize(parentContext));
        appendSentence(clauses, normalize(text).isBlank() ? "" : "text " + normalize(text));
        return String.join(". ", clauses);
    }

    private static String joinSignals(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank() && !parts.contains(normalized)) {
                parts.add(normalized);
            }
        }
        return String.join(" | ", parts);
    }

    private static void appendSentence(List<String> clauses, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            clauses.add(normalized);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
