package com.visual.locator;

import com.visual.model.ExtractedElementMetadata;
import com.visual.semantic.BrowserSemanticScripts;
import com.visual.semantic.SemanticSignalExtractor;
import com.visual.semantic.SemanticSignals;

import org.openqa.selenium.By;
import org.openqa.selenium.InvalidSelectorException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class SmartLocatorBuilder {
    private static final String LOG_PREFIX = "[SMART-LOCATOR]";
    private static final Pattern CSS_SIMPLE_ID = Pattern.compile("[A-Za-z_][-A-Za-z0-9_]*");
    private static final Pattern DYNAMIC_PATTERN =
        Pattern.compile("(?i)([a-f0-9]{8,}|\\d{4,}|_[a-f0-9]{6,}|-[a-f0-9]{6,})");
    private static final Set<String> GENERIC_CLASS_TOKENS = Set.of(
        "active", "selected", "focus", "focused", "row", "col", "container", "wrapper"
    );

    private final WebDriver driver;
    private final JavascriptExecutor js;
    private final SemanticSignalExtractor semanticExtractor;

    public SmartLocatorBuilder(WebDriver driver) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
        this.semanticExtractor = new SemanticSignalExtractor();
    }

    public SmartLocatorResult buildLocatorFromPoint(int x, int y) {
        List<String> logs = new ArrayList<>();
        log(logs, "input x=%d y=%d", x, y);

        ExtractedElementMetadata extracted = detectElementFromPoint(x, y);
        return buildLocator(extracted, logs);
    }

    public SmartLocatorResult buildLocatorForElement(WebElement element) {
        List<String> logs = new ArrayList<>();
        log(logs, "input element=%s", element);

        ExtractedElementMetadata extracted = detectElementFromElement(element);
        return buildLocator(extracted, logs);
    }

    private SmartLocatorResult buildLocator(ExtractedElementMetadata extracted, List<String> logs) {
        log(logs, "detected normalized tag=%s text='%s' label='%s' accessible='%s' role='%s' autocomplete='%s'",
            extracted.tagName, extracted.text, extracted.labelText, extracted.accessibleName, extracted.semanticRole, extracted.autocomplete);
        log(logs,
            "attributes id='%s' name='%s' class='%s' data-testid='%s' data-test='%s' aria-label='%s' placeholder='%s' type='%s' role='%s' autocomplete='%s' parent='%s'",
            extracted.id, extracted.name, extracted.className, extracted.dataTestId, extracted.dataTest,
            extracted.ariaLabel, extracted.placeholder, extracted.type, extracted.semanticRole, extracted.autocomplete, extracted.parentText);

        List<Candidate> generated = generateCandidates(extracted, logs);
        List<Candidate> accepted = new ArrayList<>();
        for (Candidate candidate : generated) {
            evaluateCandidate(candidate, extracted, logs);
            if (candidate.rejectedReason == null) {
                accepted.add(candidate);
            }
        }

        accepted.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        if (accepted.isEmpty()) {
            throw new NoSuchElementException("No unique smart locator candidate for element tag=" + extracted.tagName);
        }

        Candidate best = accepted.get(0);
        List<SmartLocatorResult.LocatorCandidate> top = accepted.stream()
            .limit(3)
            .map(c -> new SmartLocatorResult.LocatorCandidate(c.locatorType, c.locator, c.score, c.strategy))
            .toList();
        log(logs, "selected %s=%s strategy=%s score=%.3f", best.locatorType, best.locator, best.strategy, best.score);
        return new SmartLocatorResult(best.locatorType, best.locator, best.score, best.strategy,
            extracted.tagName, extracted.accessibleName, extracted.semanticRole, extracted.autocomplete, top, logs);
    }

    @SuppressWarnings("unchecked")
    private ExtractedElementMetadata detectElementFromPoint(int x, int y) {
        Object raw = js.executeScript(BrowserSemanticScripts.locatorExtractionScript("document.elementFromPoint(arguments[0], arguments[1])"), x, y);
        return withSemanticSignals(toExtractedElement(raw, "No DOM element found at point (" + x + "," + y + ")"));
    }

    @SuppressWarnings("unchecked")
    private ExtractedElementMetadata detectElementFromElement(WebElement element) {
        Object raw = js.executeScript(BrowserSemanticScripts.locatorExtractionScript("arguments[0]"), element);
        return withSemanticSignals(toExtractedElement(raw, "Could not normalize DOM element " + element));
    }

    @SuppressWarnings("unchecked")
    private ExtractedElementMetadata toExtractedElement(Object raw, String errorMessage) {
        if (!(raw instanceof List<?> parts) || parts.size() < 2 || !(parts.get(0) instanceof WebElement element)) {
            throw new NoSuchElementException(errorMessage);
        }
        Map<String, Object> meta = (Map<String, Object>) parts.get(1);
        return new ExtractedElementMetadata(
            element,
            str(meta.get("tagName")),
            str(meta.get("id")),
            str(meta.get("name")),
            str(meta.get("className")),
            str(meta.get("dataTestId")),
            str(meta.get("dataTest")),
            str(meta.get("ariaLabel")),
            str(meta.get("placeholder")),
            normalizeSpace(str(meta.get("accessibleName"))),
            str(meta.get("type")),
            normalizeSpace(str(meta.get("text"))),
            normalizeSpace(str(meta.get("labelText"))),
            normalizeSpace(str(meta.get("parentText"))),
            str(meta.get("role")),
            str(meta.get("autocomplete")),
            str(meta.get("contentEditable")),
            str(meta.get("ancestorId")),
            str(meta.get("ancestorDataTestId")),
            str(meta.get("ancestorDataTest")),
            str(meta.get("ancestorClassName")),
            str(meta.get("ancestorTagName"))
        );
    }

    private ExtractedElementMetadata withSemanticSignals(ExtractedElementMetadata extracted) {
        SemanticSignals signals = semanticExtractor.extract(driver, extracted.element);
        return new ExtractedElementMetadata(
            extracted.element,
            extracted.tagName,
            extracted.id,
            extracted.name,
            extracted.className,
            extracted.dataTestId,
            extracted.dataTest,
            extracted.ariaLabel,
            extracted.placeholder,
            firstNonBlank(signals.getAccessibleName(), extracted.accessibleName),
            extracted.type,
            extracted.text,
            extracted.labelText,
            extracted.parentText,
            firstNonBlank(signals.getSemanticRole(), extracted.semanticRole),
            firstNonBlank(signals.getAutocomplete(), extracted.autocomplete),
            extracted.contentEditable,
            extracted.ancestorId,
            extracted.ancestorDataTestId,
            extracted.ancestorDataTest,
            extracted.ancestorClassName,
            extracted.ancestorTagName
        );
    }

    private List<Candidate> generateCandidates(ExtractedElementMetadata extracted, List<String> logs) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        addCssAttributeCandidate(candidates, seen, "data-testid", extracted.dataTestId, "data-testid", 1.00, 0.98, logs);
        addCssAttributeCandidate(candidates, seen, "data-test", extracted.dataTest, "data-test", 0.96, 0.96, logs);

        if (isStableValue(extracted.id)) {
            String locator = CSS_SIMPLE_ID.matcher(extracted.id).matches()
                ? "#" + extracted.id
                : "[id=\"" + cssValue(extracted.id) + "\"]";
            addCandidate(candidates, seen, "css", locator, "id", 0.92, 0.92, logs);
        } else if (!extracted.id.isBlank()) {
            log(logs, "rejected raw id='%s' reason=dynamic-or-fragile", extracted.id);
        }

        if (isStableValue(extracted.name)) {
            addCandidate(candidates, seen, "css",
                extracted.tagName + "[name=\"" + cssValue(extracted.name) + "\"]",
                "name", 0.82, 0.88, logs);
        }
        if (isStableValue(extracted.autocomplete)) {
            String tag = extracted.tagName.isBlank() ? "" : extracted.tagName;
            addCandidate(candidates, seen, "css",
                tag + "[autocomplete=\"" + cssValue(extracted.autocomplete) + "\"]",
                "autocomplete", 0.86, 0.94, logs);
        }
        if (isStableValue(extracted.ariaLabel)) {
            addCandidate(candidates, seen, "css",
                "[aria-label=\"" + cssValue(extracted.ariaLabel) + "\"]",
                "aria-label", 0.80, 0.90, logs);
        }
        if (isStableValue(extracted.placeholder)) {
            String tag = extracted.contentEditable.equalsIgnoreCase("true") ? "[contenteditable=\"true\"]" : extracted.tagName;
            addCandidate(candidates, seen, "css",
                tag + "[placeholder=\"" + cssValue(extracted.placeholder) + "\"]",
                "placeholder", 0.60, 0.78, logs);
            if (extracted.contentEditable.equalsIgnoreCase("true")) {
                addCandidate(candidates, seen, "css",
                    "[contenteditable=\"true\"][data-placeholder=\"" + cssValue(extracted.placeholder) + "\"]",
                    "contenteditable-placeholder", 0.62, 0.82, logs);
            }
        }

        if (isStableValue(extracted.text) && (isTextAction(extracted) || isClickableContainer(extracted))) {
            if (Objects.equals(extracted.tagName, "button") || Objects.equals(extracted.tagName, "a")) {
                addCandidate(candidates, seen, "xpath",
                    "//" + extracted.tagName + "[normalize-space()=" + xpathLiteral(extracted.text) + "]",
                    "text", 0.76, 0.88, logs);
            } else {
                String textPredicate = "contains(normalize-space(.), " + xpathLiteral(extracted.text) + ")";
                String stableClass = primaryStableClass(extracted.className);
                if (isStableClassToken(stableClass)) {
                    addCandidate(candidates, seen, "css",
                        extracted.tagName + "." + stableClass,
                        "class", 0.66, 0.84, logs);
                    addCandidate(candidates, seen, "xpath",
                        "//" + extracted.tagName + "[contains(concat(' ', normalize-space(@class), ' '), ' " + stableClass + " ') and " + textPredicate + "]",
                        "class-text", 0.74, 0.83, logs);
                }
                addCandidate(candidates, seen, "xpath",
                    "//" + extracted.tagName + "[" + textPredicate + "]",
                    "text", 0.70, 0.74, logs);
            }
        }

        if (shouldUseLabelBased(extracted) && isStableValue(extracted.labelText)) {
            String labelLiteral = xpathLiteral(extracted.labelText);
            String followPredicate = xpathTagPredicate(extracted);
            addCandidate(candidates, seen, "xpath",
                "//*[self::label or self::span or self::div][normalize-space()=" + labelLiteral + "]/following-sibling::*[" + followPredicate + "][1]",
                "label-based", 0.78, 0.90, logs);
            addCandidate(candidates, seen, "xpath",
                "//*[self::label or self::span or self::div][normalize-space()=" + labelLiteral + "]/following::*[" + followPredicate + "][1]",
                "label-based", 0.74, 0.82, logs);
        }

        String ancestorPrefix = stableAncestorSelector(extracted);
        String stableClass = primaryStableClass(extracted.className);
        if (!ancestorPrefix.isBlank()) {
            if (isStableValue(extracted.name)) {
                addCandidate(candidates, seen, "css",
                    ancestorPrefix + " " + extracted.tagName + "[name=\"" + cssValue(extracted.name) + "\"]",
                    "class-attribute", 0.68, 0.88, logs);
            }
            if (isStableValue(extracted.type) && Objects.equals(extracted.tagName, "input")) {
                addCandidate(candidates, seen, "css",
                    ancestorPrefix + " input[type=\"" + cssValue(extracted.type) + "\"]",
                    "class-attribute", 0.66, 0.84, logs);
            }
            if (isStableClassToken(stableClass)) {
                addCandidate(candidates, seen, "css",
                    ancestorPrefix + " " + extracted.tagName + "." + stableClass,
                    "class-attribute", 0.64, 0.76, logs);
            }
            if (extracted.contentEditable.equalsIgnoreCase("true") && isStableValue(extracted.placeholder)) {
                addCandidate(candidates, seen, "css",
                    ancestorPrefix + " [contenteditable=\"true\"][data-placeholder=\"" + cssValue(extracted.placeholder) + "\"]",
                    "class-attribute", 0.67, 0.82, logs);
            }
        }

        if (candidates.isEmpty()) {
            throw new NoSuchElementException("No locator candidates generated for element tag=" + extracted.tagName);
        }
        return candidates;
    }

    private void evaluateCandidate(Candidate candidate, ExtractedElementMetadata extracted, List<String> logs) {
        By by = "xpath".equals(candidate.locatorType) ? By.xpath(candidate.locator) : By.cssSelector(candidate.locator);
        List<WebElement> matches;
        try {
            matches = driver.findElements(by);
        } catch (InvalidSelectorException e) {
            candidate.rejectedReason = "invalid selector: " + e.getMessage();
            log(logs, "rejected %s=%s reason=%s", candidate.locatorType, candidate.locator, candidate.rejectedReason);
            return;
        }

        if (matches.size() != 1) {
            candidate.rejectedReason = matches.isEmpty() ? "no match" : "non-unique count=" + matches.size();
            log(logs, "rejected %s=%s reason=%s", candidate.locatorType, candidate.locator, candidate.rejectedReason);
            return;
        }
        if (!sameElement(matches.get(0), extracted.element)) {
            candidate.rejectedReason = "unique but points to different element";
            log(logs, "rejected %s=%s reason=%s", candidate.locatorType, candidate.locator, candidate.rejectedReason);
            return;
        }

        double readability = readability(candidate);
        double stability = stability(candidate);
        candidate.score = roundScore(0.4 * candidate.attributeQuality + 0.3 + 0.2 * stability + 0.1 * readability);
        log(logs,
            "accepted %s=%s strategy=%s attributeQuality=%.2f stability=%.2f readability=%.2f score=%.3f",
            candidate.locatorType, candidate.locator, candidate.strategy, candidate.attributeQuality, stability, readability, candidate.score);
    }

    private boolean sameElement(WebElement a, WebElement b) {
        return Boolean.TRUE.equals(js.executeScript("return arguments[0] === arguments[1];", a, b));
    }

    private void addCssAttributeCandidate(List<Candidate> candidates, Set<String> seen, String attribute,
                                          String value, String strategy, double attributeQuality,
                                          double stabilityWeight, List<String> logs) {
        if (isStableValue(value)) {
            addCandidate(candidates, seen, "css",
                "[" + attribute + "=\"" + cssValue(value) + "\"]",
                strategy, attributeQuality, stabilityWeight, logs);
        } else if (!value.isBlank()) {
            log(logs, "rejected raw %s='%s' reason=dynamic-or-fragile", attribute, value);
        }
    }

    private void addCandidate(List<Candidate> candidates, Set<String> seen, String locatorType, String locator,
                              String strategy, double attributeQuality, double stabilityWeight, List<String> logs) {
        String key = locatorType + "::" + locator;
        if (locator.isBlank() || !seen.add(key)) {
            return;
        }
        candidates.add(new Candidate(locatorType, locator, strategy, attributeQuality, stabilityWeight));
        log(logs, "generated %s=%s strategy=%s", locatorType, locator, strategy);
    }

    private static boolean isTextAction(ExtractedElementMetadata extracted) {
        return Objects.equals(extracted.tagName, "button")
            || Objects.equals(extracted.tagName, "a")
            || Objects.equals(extracted.semanticRole, "button")
            || Objects.equals(extracted.semanticRole, "link");
    }

    private static boolean isClickableContainer(ExtractedElementMetadata extracted) {
        return !extracted.text.isBlank() && (!extracted.semanticRole.isBlank()
            || extracted.contentEditable.equalsIgnoreCase("true")
            || isStableClassToken(primaryStableClass(extracted.className)));
    }

    private static boolean shouldUseLabelBased(ExtractedElementMetadata extracted) {
        if (extracted.contentEditable.equalsIgnoreCase("true")) return true;
        if (Set.of("input", "textarea", "select").contains(extracted.tagName)) return true;
        return extracted.text.isBlank();
    }

    private static String xpathTagPredicate(ExtractedElementMetadata extracted) {
        if (extracted.contentEditable.equalsIgnoreCase("true")) return "@contenteditable='true'";
        if (Set.of("input", "textarea", "select", "button", "a").contains(extracted.tagName)) return "self::" + extracted.tagName;
        if (!extracted.semanticRole.isBlank()) return "@role='" + extracted.semanticRole + "'";
        return "self::" + extracted.tagName;
    }

    private static String stableAncestorSelector(ExtractedElementMetadata extracted) {
        if (isStableValue(extracted.ancestorDataTestId)) return "[data-testid=\"" + cssValue(extracted.ancestorDataTestId) + "\"]";
        if (isStableValue(extracted.ancestorDataTest)) return "[data-test=\"" + cssValue(extracted.ancestorDataTest) + "\"]";
        if (isStableValue(extracted.ancestorId)) {
            return CSS_SIMPLE_ID.matcher(extracted.ancestorId).matches()
                ? "#" + extracted.ancestorId
                : "[id=\"" + cssValue(extracted.ancestorId) + "\"]";
        }
        String classToken = primaryStableClass(extracted.ancestorClassName);
        if (isStableClassToken(classToken)) {
            return extracted.ancestorTagName.isBlank() ? "." + classToken : extracted.ancestorTagName + "." + classToken;
        }
        return "";
    }

    private static String primaryStableClass(String className) {
        for (String token : className.split("\\s+")) {
            if (isStableClassToken(token)) return token;
        }
        return "";
    }

    private static boolean isStableClassToken(String token) {
        if (token == null || token.isBlank()) return false;
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || GENERIC_CLASS_TOKENS.contains(normalized)) return false;
        return !looksDynamic(normalized);
    }

    private static boolean isStableValue(String value) {
        if (value == null) return false;
        String normalized = normalizeSpace(value);
        return !normalized.isBlank() && normalized.length() <= 80 && !looksDynamic(normalized);
    }

    private static boolean looksDynamic(String value) {
        return DYNAMIC_PATTERN.matcher(value).find();
    }

    private static double stability(Candidate candidate) {
        double score = candidate.stabilityWeight;
        if (looksDynamic(candidate.locator)) score -= 0.35;
        if (candidate.locator.contains("following::")) score -= 0.08;
        if (candidate.locator.contains("contains(")) score -= 0.05;
        if (candidate.locator.length() > 90) score -= 0.08;
        return clamp(score);
    }

    private static double readability(Candidate candidate) {
        double score = 1.0;
        score -= Math.min(0.40, candidate.locator.length() / 180.0);
        score -= count(candidate.locator, "[") * 0.03;
        score -= count(candidate.locator, "/") * 0.01;
        if ("data-testid".equals(candidate.strategy) || "id".equals(candidate.strategy)) score += 0.06;
        if ("label-based".equals(candidate.strategy)) score -= 0.04;
        return clamp(score);
    }

    private static int count(String value, String token) {
        int idx = 0;
        int found = 0;
        while ((idx = value.indexOf(token, idx)) >= 0) {
            found++;
            idx += token.length();
        }
        return found;
    }

    private static double clamp(double value) {
        return Math.max(0.05, Math.min(1.0, value));
    }

    private static double roundScore(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String cssValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String xpathLiteral(String value) {
        if (!value.contains("'")) return "'" + value + "'";
        if (!value.contains("\"")) return "\"" + value + "\"";
        StringBuilder sb = new StringBuilder("concat(");
        String[] parts = value.split("'");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", \"'\", ");
            sb.append("'").append(parts[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String normalizeSpace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return normalizeSpace(primary).isBlank() ? normalizeSpace(fallback) : normalizeSpace(primary);
    }

    private static void log(List<String> logs, String format, Object... args) {
        String line = LOG_PREFIX + " " + String.format(Locale.US, format, args);
        logs.add(line);
        System.out.println(line);
    }

    private static class Candidate {
        final String locatorType;
        final String locator;
        final String strategy;
        final double attributeQuality;
        final double stabilityWeight;
        String rejectedReason;
        double score;

        Candidate(String locatorType, String locator, String strategy, double attributeQuality, double stabilityWeight) {
            this.locatorType = locatorType;
            this.locator = locator;
            this.strategy = strategy;
            this.attributeQuality = attributeQuality;
            this.stabilityWeight = stabilityWeight;
        }
    }

}


