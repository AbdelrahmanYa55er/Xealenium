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
        "active", "selected", "focus", "focused", "row", "col", "container", "wrapper", "field", "group", "item"
    );
    private static final List<String> TEST_ATTRIBUTES = List.of(
        "data-testid", "data-test", "data-qa", "data-cy", "data-automation"
    );
    private static final Set<String> LABEL_TAGS = Set.of("label", "span", "div", "legend");
    private static final Set<String> ACTION_TAGS = Set.of("button", "a");
    private static final Set<String> CONTAINER_TAGS = Set.of("form", "section", "fieldset");

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

        LocatorContext context = resolveContext(extracted);
        log(logs, "context labelTag='%s' labelClass='%s' labelText='%s' containerTag='%s' containerClass='%s' containerId='%s'",
            context.labelTag, context.labelClass, context.labelText, context.containerTag, context.containerClass, context.containerId);

        List<Candidate> generated = generateCandidates(extracted, context, logs);
        evaluateCandidates(generated, extracted, logs);
        generated.sort(candidateComparator());

        Candidate selected = selectCandidate(generated);
        if (selected == null) {
            throw new NoSuchElementException("No smart locator candidate for element tag=" + extracted.tagName);
        }

        List<SmartLocatorResult.LocatorCandidate> rankedCandidates = new ArrayList<>();
        int rank = 1;
        for (Candidate candidate : generated) {
            if (!candidate.matchesTarget) {
                continue;
            }
            rankedCandidates.add(candidate.toResultCandidate(rank++));
        }
        if (rankedCandidates.isEmpty()) {
            rankedCandidates.add(selected.toResultCandidate(1));
        }

        SmartLocatorResult.LocatorCandidate selectedResult = rankedCandidates.stream()
            .filter(candidate -> sameBy(candidate.getBy(), selected.by))
            .findFirst()
            .orElse(rankedCandidates.get(0));

        log(logs, "selected %s=%s strategy=%s risk=%s unique=%s score=%.3f reason=%s",
            selected.locatorType, selected.locator, selected.strategy, selected.riskLevel, selected.unique, selected.score, selected.reason);

        return new SmartLocatorResult(
            extracted.tagName,
            extracted.accessibleName,
            extracted.semanticRole,
            extracted.autocomplete,
            selectedResult,
            rankedCandidates,
            logs
        );
    }

    private ExtractedElementMetadata detectElementFromPoint(int x, int y) {
        Object raw = js.executeScript(BrowserSemanticScripts.locatorExtractionScript("document.elementFromPoint(arguments[0], arguments[1])"), x, y);
        return withSemanticSignals(toExtractedElement(raw, "No DOM element found at point (" + x + "," + y + ")"));
    }

    private ExtractedElementMetadata detectElementFromElement(WebElement element) {
        Object raw = js.executeScript(BrowserSemanticScripts.locatorExtractionScript("arguments[0]"), element);
        return withSemanticSignals(toExtractedElement(raw, "Could not normalize DOM element " + element));
    }

    private ExtractedElementMetadata toExtractedElement(Object raw, String errorMessage) {
        if (!(raw instanceof List<?> parts) || parts.size() < 2 || !(parts.get(0) instanceof WebElement element)) {
            throw new NoSuchElementException(errorMessage);
        }
        if (!(parts.get(1) instanceof Map<?, ?> meta)) {
            throw new NoSuchElementException(errorMessage);
        }
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

    private LocatorContext resolveContext(ExtractedElementMetadata extracted) {
        Object raw = js.executeScript("""
            function normalize(value) {
              return value ? String(value).replace(/\\s+/g, ' ').trim() : '';
            }
            function isFieldLike(node) {
              if (!node || node.nodeType !== 1) return false;
              var tag = node.tagName.toLowerCase();
              var type = normalize(node.getAttribute('type')).toLowerCase();
              return node.isContentEditable || tag === 'textarea' || tag === 'select' || (tag === 'input' && type !== 'hidden');
            }
            function isStableClassToken(token) {
              if (!token) return false;
              var lower = token.toLowerCase();
              if (lower.length < 3) return false;
              if (/(active|selected|focus|focused|row|col|container|wrapper|field|group|item)/.test(lower)) return false;
              return !/([a-f0-9]{8,}|\\d{4,}|_[a-f0-9]{6,}|-[a-f0-9]{6,})/i.test(lower);
            }
            function firstStableClass(value) {
              if (!value) return '';
              var tokens = String(value).trim().split(/\\s+/);
              for (var i = 0; i < tokens.length; i++) {
                if (isStableClassToken(tokens[i])) return tokens[i];
              }
              return '';
            }
            function isLabelLike(node) {
              if (!node || node.nodeType !== 1) return false;
              var tag = node.tagName.toLowerCase();
              var classes = normalize(node.className).toLowerCase();
              return tag === 'label' || tag === 'span' || tag === 'div' || tag === 'legend'
                || classes.indexOf('label') >= 0 || classes.indexOf('title') >= 0;
            }
            function findLabel(node) {
              if (!node) return null;
              if (node.id) {
                var byFor = document.querySelector('label[for="' + CSS.escape(node.id) + '"]');
                if (byFor) return byFor;
              }
              var wrapped = node.closest('label');
              if (wrapped && wrapped !== node) return wrapped;
              var previous = node.previousElementSibling;
              while (previous) {
                if (isLabelLike(previous)) return previous;
                if (isFieldLike(previous)) break;
                previous = previous.previousElementSibling;
              }
              return null;
            }
            function findContainer(node) {
              var current = node ? node.parentElement : null;
              while (current) {
                var tag = current.tagName.toLowerCase();
                var id = normalize(current.id);
                var cls = normalize(current.className);
                var stableClass = firstStableClass(cls);
                if (normalize(current.getAttribute('data-testid')) || normalize(current.getAttribute('data-test'))
                    || id || tag === 'form' || tag === 'section' || tag === 'fieldset'
                    || normalize(current.getAttribute('role')) === 'form'
                    || stableClass) {
                  return current;
                }
                current = current.parentElement;
              }
              return null;
            }
            var element = arguments[0];
            var label = findLabel(element);
            var container = findContainer(element);
            return {
              labelTag: label ? label.tagName.toLowerCase() : '',
              labelClass: label ? normalize(label.className) : '',
              labelText: label ? normalize(label.innerText || label.textContent) : '',
              containerTag: container ? container.tagName.toLowerCase() : '',
              containerClass: container ? normalize(container.className) : '',
              containerId: container ? normalize(container.id) : '',
              containerDataTestId: container ? normalize(container.getAttribute('data-testid')) : '',
              containerDataTest: container ? normalize(container.getAttribute('data-test')) : '',
              containerRole: container ? normalize(container.getAttribute('role')) : ''
            };
            """, extracted.element);

        if (!(raw instanceof Map<?, ?> meta)) {
            return new LocatorContext("", "", "", "", "", "", "", "", "");
        }
        return new LocatorContext(
            str(meta.get("labelTag")),
            str(meta.get("labelClass")),
            normalizeSpace(str(meta.get("labelText"))),
            str(meta.get("containerTag")),
            str(meta.get("containerClass")),
            str(meta.get("containerId")),
            str(meta.get("containerDataTestId")),
            str(meta.get("containerDataTest")),
            str(meta.get("containerRole"))
        );
    }

    private List<Candidate> generateCandidates(ExtractedElementMetadata extracted, LocatorContext context, List<String> logs) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (isStableValue(extracted.id)) {
            String locator = CSS_SIMPLE_ID.matcher(extracted.id).matches()
                ? "#" + extracted.id
                : "[id='" + cssLiteral(extracted.id) + "']";
            addCandidate(candidates, seen, "css", locator, "id", 1, "unique id", SmartLocatorResult.RiskLevel.LOW, logs);
        } else if (!normalizeSpace(extracted.id).isBlank()) {
            log(logs, "rejected raw id='%s' reason=dynamic-or-fragile", extracted.id);
        }

        addStableAttributeCandidate(candidates, seen, extracted.dataTestId, "data-testid", 2, logs);
        addStableAttributeCandidate(candidates, seen, extracted.dataTest, "data-test", 2, logs);
        addStableAttributeCandidate(candidates, seen, attributeValue(extracted.element, "data-qa"), "data-qa", 2, logs);
        addStableAttributeCandidate(candidates, seen, attributeValue(extracted.element, "data-cy"), "data-cy", 2, logs);
        addStableAttributeCandidate(candidates, seen, attributeValue(extracted.element, "data-automation"), "data-automation", 2, logs);

        addAccessibilityCandidates(candidates, seen, extracted, logs);
        addNameCandidates(candidates, seen, extracted, logs);
        addSemanticCssCandidates(candidates, seen, extracted, logs);
        addLabelBasedCandidates(candidates, seen, extracted, context, logs);
        addRoleAccessibleNameXPathCandidates(candidates, seen, extracted, logs);
        addActionCandidates(candidates, seen, extracted, logs);
        addGenericFallbackCandidates(candidates, seen, extracted, context, logs);

        if (candidates.isEmpty()) {
            throw new NoSuchElementException("No locator candidates generated for element tag=" + extracted.tagName);
        }
        return candidates;
    }

    private void addAccessibilityCandidates(List<Candidate> candidates, Set<String> seen,
                                            ExtractedElementMetadata extracted, List<String> logs) {
        if (isStableValue(extracted.ariaLabel)) {
            addCandidate(candidates, seen, "css",
                "[aria-label='" + cssLiteral(extracted.ariaLabel) + "']",
                "aria-label", 3, "aria-label", SmartLocatorResult.RiskLevel.LOW, logs);
            if (isStableValue(extracted.semanticRole)) {
                addCandidate(candidates, seen, "css",
                    "[role='" + cssLiteral(extracted.semanticRole) + "'][aria-label='" + cssLiteral(extracted.ariaLabel) + "']",
                    "role-aria-label", 3, "role + aria-label", SmartLocatorResult.RiskLevel.LOW, logs);
            }
        }

        String ariaLabelledBy = attributeValue(extracted.element, "aria-labelledby");
        if (isStableValue(ariaLabelledBy)) {
            addCandidate(candidates, seen, "css",
                "[aria-labelledby='" + cssLiteral(ariaLabelledBy) + "']",
                "aria-labelledby", 3, "aria-labelledby", SmartLocatorResult.RiskLevel.LOW, logs);
        }
    }

    private void addNameCandidates(List<Candidate> candidates, Set<String> seen,
                                   ExtractedElementMetadata extracted, List<String> logs) {
        if (!isStableValue(extracted.name)) {
            return;
        }
        String tag = actualTag(extracted);
        addCandidate(candidates, seen, "css",
            tag + "[name='" + cssLiteral(extracted.name) + "']",
            "name", 4, "name attribute", SmartLocatorResult.RiskLevel.LOW, logs);
        addCandidate(candidates, seen, "css",
            "[name='" + cssLiteral(extracted.name) + "']",
            "name", 4, "name attribute", SmartLocatorResult.RiskLevel.LOW, logs);
    }

    private void addSemanticCssCandidates(List<Candidate> candidates, Set<String> seen,
                                          ExtractedElementMetadata extracted, List<String> logs) {
        String tag = actualTag(extracted);
        String stableClass = primaryStableClass(extracted.className);

        if ("input".equals(tag) && isStableValue(extracted.type)) {
            addCandidate(candidates, seen, "css",
                "input[type='" + cssLiteral(extracted.type) + "']",
                "semantic-css", 5, "stable semantic css", SmartLocatorResult.RiskLevel.LOW, logs);
        }
        if ("button".equals(tag) && isStableValue(extracted.type)) {
            addCandidate(candidates, seen, "css",
                "button[type='" + cssLiteral(extracted.type) + "']",
                "semantic-css", 5, "stable semantic css", SmartLocatorResult.RiskLevel.LOW, logs);
        }
        if (isStableClassToken(stableClass)) {
            addCandidate(candidates, seen, "css",
                tag + "." + stableClass,
                "semantic-css", 5, "stable semantic css", SmartLocatorResult.RiskLevel.MEDIUM, logs);
        }
        if (extracted.contentEditable.equalsIgnoreCase("true") && isStableValue(extracted.ariaLabel)) {
            addCandidate(candidates, seen, "css",
                tag + "[contenteditable='true'][aria-label='" + cssLiteral(extracted.ariaLabel) + "']",
                "semantic-css", 5, "stable semantic css", SmartLocatorResult.RiskLevel.LOW, logs);
        }
        if (ACTION_TAGS.contains(tag) && isStableClassToken(stableClass)) {
            addCandidate(candidates, seen, "css",
                tag + "." + stableClass,
                "semantic-css", 5, "stable semantic css", SmartLocatorResult.RiskLevel.MEDIUM, logs);
        }
    }

    private void addLabelBasedCandidates(List<Candidate> candidates, Set<String> seen,
                                         ExtractedElementMetadata extracted, LocatorContext context, List<String> logs) {
        String labelText = firstNonBlank(context.labelText, extracted.labelText);
        if (!shouldUseLabelBased(extracted) || !isStableValue(labelText)) {
            return;
        }

        String containerPrefix = containerXPathPrefix(extracted, context);
        String labelSelector = labelXPathSelector(context, labelText);
        String targetTag = actualTag(extracted);
        String targetPredicate = targetXPathPredicate(extracted);

        addCandidate(candidates, seen, "xpath",
            containerPrefix + labelSelector + "/following-sibling::" + targetTag + "[" + targetPredicate + "][1]",
            "label-based", 6, "label-based " + targetTag + " field", SmartLocatorResult.RiskLevel.MEDIUM, logs);

        if (!containerPrefix.isBlank()) {
            addCandidate(candidates, seen, "xpath",
                containerPrefix + "//" + labelSelectorWithoutPrefix(context, labelText) + "/following-sibling::" + targetTag + "[" + targetPredicate + "][1]",
                "label-based", 6, "label-based " + targetTag + " field", SmartLocatorResult.RiskLevel.MEDIUM, logs);
        }

        addCandidate(candidates, seen, "xpath",
            containerPrefix + labelSelector + "/following::" + targetTag + "[" + targetPredicate + "][1]",
            "label-based", 6, "label-based " + targetTag + " field", SmartLocatorResult.RiskLevel.MEDIUM, logs);
    }

    private void addRoleAccessibleNameXPathCandidates(List<Candidate> candidates, Set<String> seen,
                                                      ExtractedElementMetadata extracted, List<String> logs) {
        if (isStableValue(extracted.semanticRole) && isStableValue(extracted.ariaLabel)) {
            addCandidate(candidates, seen, "xpath",
                "//*[@role=" + xpathLiteral(extracted.semanticRole) + " and @aria-label=" + xpathLiteral(extracted.ariaLabel) + "]",
                "role-accessible-name", 7, "role + accessible-name", SmartLocatorResult.RiskLevel.LOW, logs);
        }
    }

    private void addActionCandidates(List<Candidate> candidates, Set<String> seen,
                                     ExtractedElementMetadata extracted, List<String> logs) {
        if (!isTextAction(extracted) || !isStableValue(extracted.text)) {
            return;
        }

        String tag = actualTag(extracted);
        String stableClass = primaryStableClass(extracted.className);
        if (ACTION_TAGS.contains(tag) && isStableClassToken(stableClass)) {
            addCandidate(candidates, seen, "xpath",
                "//" + tag + "[contains(@class," + xpathLiteral(stableClass) + ") and normalize-space()=" + xpathLiteral(extracted.text) + "]",
                "text-action", 8, "text-based action", SmartLocatorResult.RiskLevel.MEDIUM, logs);
        }
        addCandidate(candidates, seen, "xpath",
            "//*[(self::button or self::a) and normalize-space()=" + xpathLiteral(extracted.text) + "]",
            "text-action", 8, "text-based action", SmartLocatorResult.RiskLevel.MEDIUM, logs);
    }

    private void addGenericFallbackCandidates(List<Candidate> candidates, Set<String> seen,
                                              ExtractedElementMetadata extracted, LocatorContext context, List<String> logs) {
        String labelText = firstNonBlank(context.labelText, extracted.labelText);
        String targetTag = actualTag(extracted);
        String targetPredicate = targetXPathPredicate(extracted);

        if (isStableValue(labelText)) {
            String labelTagGroup = labelTagGroup(context.labelTag);
            addCandidate(candidates, seen, "xpath",
                "//*[" + labelTagGroup + " and normalize-space()=" + xpathLiteral(labelText) + "]/following::*[" + targetPredicate + "][1]",
                "generic-fallback", 9, "generic semantic fallback", SmartLocatorResult.RiskLevel.HIGH, logs);
        }

        addCandidate(candidates, seen, "xpath",
            "(//" + targetTag + "[" + targetPredicate + "])[1]",
            "positional-fallback", 10, "positional fallback", SmartLocatorResult.RiskLevel.HIGH, logs);
    }

    private void evaluateCandidates(List<Candidate> candidates, ExtractedElementMetadata extracted, List<String> logs) {
        for (Candidate candidate : candidates) {
            candidate.by = "xpath".equals(candidate.locatorType) ? By.xpath(candidate.locator) : By.cssSelector(candidate.locator);
            List<WebElement> matches;
            try {
                matches = driver.findElements(candidate.by);
            } catch (InvalidSelectorException e) {
                candidate.rejectedReason = "invalid selector: " + e.getMessage();
                log(logs, "rejected %s=%s reason=%s", candidate.locatorType, candidate.locator, candidate.rejectedReason);
                continue;
            }

            candidate.matchCount = matches.size();
            candidate.unique = matches.size() == 1;
            candidate.matchesTarget = false;
            for (WebElement match : matches) {
                if (sameElement(match, extracted.element)) {
                    candidate.matchesTarget = true;
                    break;
                }
            }

            if (!candidate.matchesTarget) {
                candidate.rejectedReason = matches.isEmpty() ? "no match" : "does not target selected element";
                log(logs, "rejected %s=%s reason=%s", candidate.locatorType, candidate.locator, candidate.rejectedReason);
                continue;
            }

            if (!candidate.unique) {
                candidate.rejectedReason = "non-unique count=" + matches.size();
            }

            candidate.score = computeCandidateScore(candidate);
            String outcome = candidate.unique ? "accepted" : "fallback";
            log(logs,
                "%s %s=%s strategy=%s reason=%s risk=%s unique=%s matchCount=%d score=%.3f",
                outcome, candidate.locatorType, candidate.locator, candidate.strategy, candidate.reason,
                candidate.riskLevel, candidate.unique, candidate.matchCount, candidate.score);
        }
    }

    private Candidate selectCandidate(List<Candidate> candidates) {
        return candidates.stream()
            .filter(candidate -> candidate.matchesTarget)
            .filter(candidate -> candidate.unique)
            .findFirst()
            .orElseGet(() -> candidates.stream()
                .filter(candidate -> candidate.matchesTarget)
                .findFirst()
                .orElse(null));
    }

    private Comparator<Candidate> candidateComparator() {
        return Comparator
            .comparing((Candidate candidate) -> !candidate.matchesTarget)
            .thenComparing((Candidate candidate) -> !candidate.unique)
            .thenComparing(candidate -> candidate.riskLevel.ordinal())
            .thenComparingInt(candidate -> candidate.priority)
            .thenComparing((Candidate candidate) -> !"css".equals(candidate.locatorType))
            .thenComparing((Candidate candidate) -> candidate.matchCount)
            .thenComparingInt(candidate -> specificityPenalty(candidate.locatorType, candidate.locator))
            .thenComparing(Comparator.comparingDouble((Candidate candidate) -> candidate.score).reversed());
    }

    private double computeCandidateScore(Candidate candidate) {
        double base = 1.05 - (candidate.priority * 0.06);
        if ("css".equals(candidate.locatorType)) {
            base += 0.06;
        }
        if (!candidate.unique) {
            base -= 0.30;
        }
        base -= candidate.riskLevel.ordinal() * 0.15;
        base -= specificityPenalty(candidate.locatorType, candidate.locator) * 0.01;
        return roundScore(Math.max(0.05, Math.min(1.0, base)));
    }

    private int specificityPenalty(String locatorType, String locator) {
        int penalty = 0;
        if ("xpath".equals(locatorType)) {
            if (locator.contains("//*[")) {
                penalty += 6;
            }
            if (locator.contains("following::*")) {
                penalty += 4;
            }
            if (locator.contains("(//")) {
                penalty += 10;
            }
            if (locator.contains("/following-sibling::")) {
                penalty -= 2;
            }
            if (locator.contains("contains(@class")) {
                penalty -= 1;
            }
        }
        if (locator.length() > 140) {
            penalty += 2;
        }
        return Math.max(0, penalty);
    }

    private boolean sameElement(WebElement left, WebElement right) {
        return Boolean.TRUE.equals(js.executeScript("return arguments[0] === arguments[1];", left, right));
    }

    private boolean sameBy(By left, By right) {
        return left != null && right != null && Objects.equals(left.toString(), right.toString());
    }

    private void addStableAttributeCandidate(List<Candidate> candidates, Set<String> seen,
                                             String value, String attributeName, int priority, List<String> logs) {
        if (!isStableValue(value)) {
            return;
        }
        addCandidate(candidates, seen, "css",
            "[" + attributeName + "='" + cssLiteral(value) + "']",
            attributeName, priority, "stable test attribute", SmartLocatorResult.RiskLevel.LOW, logs);
    }

    private void addCandidate(List<Candidate> candidates, Set<String> seen, String locatorType, String locator,
                              String strategy, int priority, String reason,
                              SmartLocatorResult.RiskLevel riskLevel, List<String> logs) {
        if (locator == null || locator.isBlank()) {
            return;
        }
        String key = locatorType + "::" + locator;
        if (!seen.add(key)) {
            return;
        }
        candidates.add(new Candidate(locatorType, locator, strategy, priority, reason, riskLevel));
        log(logs, "generated %s=%s strategy=%s reason=%s risk=%s", locatorType, locator, strategy, reason, riskLevel);
    }

    private static String actualTag(ExtractedElementMetadata extracted) {
        if (normalizeSpace(extracted.tagName).isBlank()) {
            return extracted.contentEditable.equalsIgnoreCase("true") ? "div" : "*";
        }
        return extracted.tagName;
    }

    private static boolean isTextAction(ExtractedElementMetadata extracted) {
        return ACTION_TAGS.contains(extracted.tagName)
            || Objects.equals(extracted.semanticRole, "button")
            || Objects.equals(extracted.semanticRole, "link");
    }

    private static boolean shouldUseLabelBased(ExtractedElementMetadata extracted) {
        if (extracted.contentEditable.equalsIgnoreCase("true")) {
            return true;
        }
        return Set.of("input", "textarea", "select").contains(extracted.tagName);
    }

    private static String targetXPathPredicate(ExtractedElementMetadata extracted) {
        if (extracted.contentEditable.equalsIgnoreCase("true")) {
            return "@contenteditable='true'";
        }
        if (Set.of("input", "textarea", "select", "button", "a", "div").contains(extracted.tagName)) {
            return "self::" + extracted.tagName;
        }
        if (!normalizeSpace(extracted.semanticRole).isBlank()) {
            return "@role=" + xpathLiteral(extracted.semanticRole);
        }
        return "self::*";
    }

    private static String containerXPathPrefix(ExtractedElementMetadata extracted, LocatorContext context) {
        if (isStableValue(context.containerDataTestId)) {
            return "//*[@data-testid=" + xpathLiteral(context.containerDataTestId) + "]";
        }
        if (isStableValue(context.containerDataTest)) {
            return "//*[@data-test=" + xpathLiteral(context.containerDataTest) + "]";
        }
        if (isStableValue(context.containerId)) {
            return "//*[@id=" + xpathLiteral(context.containerId) + "]";
        }
        String classToken = primaryStableClass(context.containerClass);
        if (isStableClassToken(classToken)) {
            String tag = normalizeSpace(context.containerTag).isBlank() ? "*" : context.containerTag;
            return "//" + tag + "[contains(@class," + xpathLiteral(classToken) + ")]";
        }
        if (CONTAINER_TAGS.contains(context.containerTag)) {
            return "//" + context.containerTag;
        }
        if (Objects.equals(context.containerRole, "form")) {
            return "//*[@role='form']";
        }
        if (isStableValue(extracted.ancestorId)) {
            return "//*[@id=" + xpathLiteral(extracted.ancestorId) + "]";
        }
        String ancestorClass = primaryStableClass(extracted.ancestorClassName);
        if (isStableClassToken(ancestorClass)) {
            String tag = normalizeSpace(extracted.ancestorTagName).isBlank() ? "*" : extracted.ancestorTagName;
            return "//" + tag + "[contains(@class," + xpathLiteral(ancestorClass) + ")]";
        }
        return "";
    }

    private static String labelXPathSelector(LocatorContext context, String labelText) {
        return "//" + labelXPathSelectorWithoutPrefix(context, labelText);
    }

    private static String labelSelectorWithoutPrefix(LocatorContext context, String labelText) {
        return labelXPathSelectorWithoutPrefix(context, labelText);
    }

    private static String labelXPathSelectorWithoutPrefix(LocatorContext context, String labelText) {
        String tag = LABEL_TAGS.contains(context.labelTag) ? context.labelTag : "*";
        List<String> predicates = new ArrayList<>();
        String classToken = primaryStableClass(context.labelClass);
        if (isStableClassToken(classToken)) {
            predicates.add("contains(@class," + xpathLiteral(classToken) + ")");
        }
        predicates.add("normalize-space()=" + xpathLiteral(labelText));
        return tag + "[" + String.join(" and ", predicates) + "]";
    }

    private static String labelTagGroup(String labelTag) {
        if (LABEL_TAGS.contains(labelTag)) {
            return "self::" + labelTag;
        }
        return "self::label or self::span or self::div or self::legend";
    }

    private static String attributeValue(WebElement element, String attributeName) {
        String value = element.getAttribute(attributeName);
        return value == null ? "" : normalizeSpace(value);
    }

    private static String primaryStableClass(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        for (String token : className.split("\\s+")) {
            if (isStableClassToken(token)) {
                return token;
            }
        }
        return "";
    }

    private static boolean isStableClassToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || GENERIC_CLASS_TOKENS.contains(normalized)) {
            return false;
        }
        return !looksDynamic(normalized);
    }

    private static boolean isStableValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = normalizeSpace(value);
        return !normalized.isBlank() && normalized.length() <= 120 && !looksDynamic(normalized);
    }

    private static boolean looksDynamic(String value) {
        return DYNAMIC_PATTERN.matcher(value).find();
    }

    private static double roundScore(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String cssLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        StringBuilder builder = new StringBuilder("concat(");
        String[] parts = value.split("'");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(", \"'\", ");
            }
            builder.append("'").append(parts[i]).append("'");
        }
        builder.append(")");
        return builder.toString();
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

    private static final class Candidate {
        final String locatorType;
        final String locator;
        final String strategy;
        final int priority;
        final String reason;
        final SmartLocatorResult.RiskLevel riskLevel;
        By by;
        boolean unique;
        boolean matchesTarget;
        int matchCount;
        double score;
        String rejectedReason;

        private Candidate(String locatorType, String locator, String strategy, int priority,
                          String reason, SmartLocatorResult.RiskLevel riskLevel) {
            this.locatorType = locatorType;
            this.locator = locator;
            this.strategy = strategy;
            this.priority = priority;
            this.reason = reason;
            this.riskLevel = riskLevel;
        }

        private SmartLocatorResult.LocatorCandidate toResultCandidate(int rank) {
            String resolvedReason = unique || rejectedReason == null
                ? reason
                : reason + " (" + rejectedReason + ")";
            return new SmartLocatorResult.LocatorCandidate(
                by,
                locatorType,
                locator,
                rank,
                unique,
                resolvedReason,
                riskLevel,
                score,
                strategy
            );
        }
    }

    private record LocatorContext(
        String labelTag,
        String labelClass,
        String labelText,
        String containerTag,
        String containerClass,
        String containerId,
        String containerDataTestId,
        String containerDataTest,
        String containerRole
    ) {
    }
}
