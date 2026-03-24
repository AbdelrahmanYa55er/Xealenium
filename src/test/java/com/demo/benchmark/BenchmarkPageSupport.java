package com.demo.benchmark;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class BenchmarkPageSupport {
    protected void assertSemanticTarget(JavascriptExecutor executor, String logicalName, WebElement element,
                                        String... expectedHints) {
        String description = semanticDescription(executor, element);
        boolean matches = Arrays.stream(expectedHints)
            .map(this::normalize)
            .anyMatch(description::contains);
        assertTrue(matches, () -> "Expected " + logicalName + " to map to matching semantics, but got: " + description);
    }

    protected static String pageUrl(String name) {
        return Path.of("pages", name).toAbsolutePath().toUri().toString();
    }

    protected String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String semanticDescription(JavascriptExecutor executor, WebElement element) {
        String script =
            "function push(parts, value) {" +
            "  if (value === null || value === undefined) return;" +
            "  value = String(value).trim();" +
            "  if (value) parts.push(value);" +
            "}" +
            "function nodeText(el) { return el && el.innerText ? el.innerText.trim() : ''; }" +
            "function attr(el, name) { return el ? String(el.getAttribute(name) || '').trim() : ''; }" +
            "function referencedText(ids) {" +
            "  if (!ids) return '';" +
            "  var parts = [];" +
            "  ids.split(/\\s+/).forEach(function(id) {" +
            "    if (!id) return;" +
            "    var ref = document.getElementById(id);" +
            "    if (ref) push(parts, nodeText(ref));" +
            "  });" +
            "  return parts.join(' ');" +
            "}" +
            "function labelLikeText(el) {" +
            "  if (!el) return '';" +
            "  var aria = referencedText(attr(el, 'aria-labelledby'));" +
            "  if (aria) return aria;" +
            "  var direct = attr(el, 'aria-label') || attr(el, 'data-label');" +
            "  if (direct) return direct;" +
            "  var txt = nodeText(el);" +
            "  if (!txt) return '';" +
            "  var tag = el.tagName.toLowerCase();" +
            "  var cls = attr(el, 'class').toLowerCase();" +
            "  if (tag === 'label' || tag === 'legend') return txt;" +
            "  if (cls.indexOf('label') >= 0 || cls.indexOf('title') >= 0) return txt;" +
            "  if (tag === 'span' || tag === 'div' || tag === 'p' || tag === 'strong') return txt;" +
            "  return '';" +
            "}" +
            "function isFieldLike(el) {" +
            "  if (!el) return false;" +
            "  var tag = el.tagName.toLowerCase();" +
            "  var type = attr(el, 'type').toLowerCase();" +
            "  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';" +
            "  if (editable || tag === 'textarea' || tag === 'select') return true;" +
            "  if (tag === 'input') return type !== 'hidden';" +
            "  return false;" +
            "}" +
            "function nearestLabelText(el) {" +
            "  var parts = [];" +
            "  push(parts, referencedText(attr(el, 'aria-labelledby')));" +
            "  var wrappedLabel = el.closest('label');" +
            "  push(parts, nodeText(wrappedLabel));" +
            "  if (el.id) {" +
            "    var byFor = document.querySelector('label[for=\"' + el.id + '\"]');" +
            "    push(parts, nodeText(byFor));" +
            "  }" +
            "  if (parts.length) return parts;" +
            "  var prev = el.previousElementSibling;" +
            "  while (prev) {" +
            "    var prevLabel = labelLikeText(prev);" +
            "    if (prevLabel) {" +
            "      push(parts, prevLabel);" +
            "      break;" +
            "    }" +
            "    if (isFieldLike(prev)) break;" +
            "    prev = prev.previousElementSibling;" +
            "  }" +
            "  return parts;" +
            "}" +
            "var el = arguments[0], parts = [];" +
            "push(parts, el.tagName);" +
            "push(parts, el.getAttribute('type'));" +
            "push(parts, el.getAttribute('role'));" +
            "push(parts, referencedText(attr(el, 'aria-labelledby')));" +
            "push(parts, el.getAttribute('placeholder'));" +
            "push(parts, el.getAttribute('data-placeholder'));" +
            "push(parts, el.getAttribute('aria-label'));" +
            "push(parts, el.getAttribute('name'));" +
            "push(parts, nodeText(el));" +
            "nearestLabelText(el).forEach(function(value) { push(parts, value); });" +
            "return [...new Set(parts)].join(' | ');";
        Object result = executor.executeScript(script, element);
        return normalize(result == null ? "" : String.valueOf(result));
    }
}
