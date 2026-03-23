package com.visual;

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

    public SmartLocatorBuilder(WebDriver driver) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
    }

    public SmartLocatorResult buildLocatorFromPoint(int x, int y) {
        List<String> logs = new ArrayList<>();
        log(logs, "input x=%d y=%d", x, y);

        ExtractedElement extracted = detectElementFromPoint(x, y);
        return buildLocator(extracted, logs);
    }

    public SmartLocatorResult buildLocatorForElement(WebElement element) {
        List<String> logs = new ArrayList<>();
        log(logs, "input element=%s", element);

        ExtractedElement extracted = detectElementFromElement(element);
        return buildLocator(extracted, logs);
    }

    private SmartLocatorResult buildLocator(ExtractedElement extracted, List<String> logs) {
        log(logs, "detected normalized tag=%s text='%s' label='%s'", extracted.tagName, extracted.text, extracted.labelText);
        log(logs,
            "attributes id='%s' name='%s' class='%s' data-testid='%s' data-test='%s' aria-label='%s' placeholder='%s' type='%s' parent='%s'",
            extracted.id, extracted.name, extracted.className, extracted.dataTestId, extracted.dataTest,
            extracted.ariaLabel, extracted.placeholder, extracted.type, extracted.parentText);

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
        return new SmartLocatorResult(best.locatorType, best.locator, best.score, best.strategy, extracted.tagName, top, logs);
    }

    @SuppressWarnings("unchecked")
    private ExtractedElement detectElementFromPoint(int x, int y) {
        Object raw = js.executeScript(locatorExtractionScript("document.elementFromPoint(arguments[0], arguments[1])"), x, y);
        return toExtractedElement(raw, "No DOM element found at point (" + x + "," + y + ")");
    }

    @SuppressWarnings("unchecked")
    private ExtractedElement detectElementFromElement(WebElement element) {
        Object raw = js.executeScript(locatorExtractionScript("arguments[0]"), element);
        return toExtractedElement(raw, "Could not normalize DOM element " + element);
    }

    private String locatorExtractionScript(String startExpression) {
        return ("""
            function trim(v) { return v ? String(v).trim() : ''; }
            function textOf(el) { return el && el.innerText ? el.innerText.replace(/\\s+/g, ' ').trim() : ''; }
            function attr(el, name) { return el ? trim(el.getAttribute(name)) : ''; }
            function isClickable(el) {
              if (!el) return false;
              var tag = el.tagName.toLowerCase();
              var role = attr(el, 'role').toLowerCase();
              if (tag === 'button' || tag === 'a' || tag === 'input' || tag === 'select' || tag === 'textarea') return true;
              if (attr(el, 'contenteditable').toLowerCase() === 'true') return true;
              if (role === 'button' || role === 'link' || role === 'checkbox' || role === 'switch' || role === 'radio') return true;
              if (el.onclick) return true;
              var tabIndex = el.getAttribute('tabindex');
              return tabIndex !== null && Number(tabIndex) >= 0;
            }
            function isMeaningful(el) {
              if (!el) return false;
              var tag = el.tagName.toLowerCase();
              if (tag === 'input' || tag === 'button' || tag === 'select' || tag === 'textarea') return true;
              if (attr(el, 'contenteditable').toLowerCase() === 'true') return true;
              if (isClickable(el) && textOf(el)) return true;
              if (isClickable(el) && tag === 'div') return true;
              return false;
            }
            function nearestLabelText(el) {
              if (!el) return '';
              if (el.id) {
                var byFor = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
                if (byFor && textOf(byFor)) return textOf(byFor);
              }
              var current = el;
              while (current && current.parentElement) {
                var prev = current.previousElementSibling;
                while (prev) {
                  var prevText = textOf(prev) || attr(prev, 'aria-label') || attr(prev, 'data-label');
                  if (prevText) return prevText;
                  prev = prev.previousElementSibling;
                }
                var parent = current.parentElement;
                var labels = parent.querySelectorAll(':scope > label, :scope > .e-title, :scope > [data-label], :scope > [aria-label]');
                for (var i = labels.length - 1; i >= 0; i--) {
                  if (labels[i] === current || labels[i].contains(current)) continue;
                  var txt = textOf(labels[i]) || attr(labels[i], 'aria-label') || attr(labels[i], 'data-label');
                  if (txt) return txt;
                }
                current = parent;
              }
              return '';
            }
            function nearestStableAncestor(el) {
              var current = el ? el.parentElement : null;
              while (current) {
                var dataTestId = attr(current, 'data-testid');
                var dataTest = attr(current, 'data-test');
                var id = attr(current, 'id');
                var cls = attr(current, 'class');
                if (dataTestId || dataTest || id || cls) {
                  return {
                    id: id, dataTestId: dataTestId, dataTest: dataTest,
                    className: cls, tagName: current.tagName.toLowerCase()
                  };
                }
                current = current.parentElement;
              }
              return { id: '', dataTestId: '', dataTest: '', className: '', tagName: '' };
            }
            function normalize(el) {
              var current = el;
              while (current) {
                if (isMeaningful(current)) return current;
                if (!current.parentElement) return current;
                current = current.parentElement;
              }
              return el;
            }
            var start = %s;
            if (!start) return null;
            var target = normalize(start);
            var ancestor = nearestStableAncestor(target);
            return [target, {
              tagName: target.tagName.toLowerCase(),
              id: attr(target, 'id'),
              name: attr(target, 'name'),
              className: attr(target, 'class'),
              dataTestId: attr(target, 'data-testid'),
              dataTest: attr(target, 'data-test'),
              ariaLabel: attr(target, 'aria-label'),
              placeholder: attr(target, 'placeholder') || attr(target, 'data-placeholder'),
              type: attr(target, 'type'),
              text: textOf(target),
              labelText: nearestLabelText(target),
              parentText: textOf(target.parentElement),
              role: attr(target, 'role'),
              contentEditable: attr(target, 'contenteditable'),
              ancestorId: ancestor.id,
              ancestorDataTestId: ancestor.dataTestId,
              ancestorDataTest: ancestor.dataTest,
              ancestorClassName: ancestor.className,
              ancestorTagName: ancestor.tagName
            }];
            """).formatted(startExpression);
    }

    @SuppressWarnings("unchecked")
    private ExtractedElement toExtractedElement(Object raw, String errorMessage) {
        if (!(raw instanceof List<?> parts) || parts.size() < 2 || !(parts.get(0) instanceof WebElement element)) {
            throw new NoSuchElementException(errorMessage);
        }
        Map<String, Object> meta = (Map<String, Object>) parts.get(1);
        return new ExtractedElement(
            element,
            str(meta.get("tagName")),
            str(meta.get("id")),
            str(meta.get("name")),
            str(meta.get("className")),
            str(meta.get("dataTestId")),
            str(meta.get("dataTest")),
            str(meta.get("ariaLabel")),
            str(meta.get("placeholder")),
            str(meta.get("type")),
            normalizeSpace(str(meta.get("text"))),
            normalizeSpace(str(meta.get("labelText"))),
            normalizeSpace(str(meta.get("parentText"))),
            str(meta.get("role")),
            str(meta.get("contentEditable")),
            str(meta.get("ancestorId")),
            str(meta.get("ancestorDataTestId")),
            str(meta.get("ancestorDataTest")),
            str(meta.get("ancestorClassName")),
            str(meta.get("ancestorTagName"))
        );
    }

    private List<Candidate> generateCandidates(ExtractedElement extracted, List<String> logs) {
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

    private void evaluateCandidate(Candidate candidate, ExtractedElement extracted, List<String> logs) {
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

    private static boolean isTextAction(ExtractedElement extracted) {
        return Objects.equals(extracted.tagName, "button")
            || Objects.equals(extracted.tagName, "a")
            || Objects.equals(extracted.role, "button")
            || Objects.equals(extracted.role, "link");
    }

    private static boolean isClickableContainer(ExtractedElement extracted) {
        return !extracted.text.isBlank() && (!extracted.role.isBlank()
            || extracted.contentEditable.equalsIgnoreCase("true")
            || isStableClassToken(primaryStableClass(extracted.className)));
    }

    private static boolean shouldUseLabelBased(ExtractedElement extracted) {
        if (extracted.contentEditable.equalsIgnoreCase("true")) return true;
        if (Set.of("input", "textarea", "select").contains(extracted.tagName)) return true;
        return extracted.text.isBlank();
    }

    private static String xpathTagPredicate(ExtractedElement extracted) {
        if (extracted.contentEditable.equalsIgnoreCase("true")) return "@contenteditable='true'";
        if (Set.of("input", "textarea", "select", "button", "a").contains(extracted.tagName)) return "self::" + extracted.tagName;
        if (!extracted.role.isBlank()) return "@role='" + extracted.role + "'";
        return "self::" + extracted.tagName;
    }

    private static String stableAncestorSelector(ExtractedElement extracted) {
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

    private static class ExtractedElement {
        final WebElement element;
        final String tagName;
        final String id;
        final String name;
        final String className;
        final String dataTestId;
        final String dataTest;
        final String ariaLabel;
        final String placeholder;
        final String type;
        final String text;
        final String labelText;
        final String parentText;
        final String role;
        final String contentEditable;
        final String ancestorId;
        final String ancestorDataTestId;
        final String ancestorDataTest;
        final String ancestorClassName;
        final String ancestorTagName;

        ExtractedElement(WebElement element, String tagName, String id, String name, String className,
                         String dataTestId, String dataTest, String ariaLabel, String placeholder, String type,
                         String text, String labelText, String parentText, String role, String contentEditable,
                         String ancestorId, String ancestorDataTestId, String ancestorDataTest,
                         String ancestorClassName, String ancestorTagName) {
            this.element = element;
            this.tagName = tagName;
            this.id = id;
            this.name = name;
            this.className = className;
            this.dataTestId = dataTestId;
            this.dataTest = dataTest;
            this.ariaLabel = ariaLabel;
            this.placeholder = placeholder;
            this.type = type;
            this.text = text;
            this.labelText = labelText;
            this.parentText = parentText;
            this.role = role;
            this.contentEditable = contentEditable;
            this.ancestorId = ancestorId;
            this.ancestorDataTestId = ancestorDataTestId;
            this.ancestorDataTest = ancestorDataTest;
            this.ancestorClassName = ancestorClassName;
            this.ancestorTagName = ancestorTagName;
        }
    }
}
