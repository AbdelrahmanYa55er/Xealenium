package com.visual;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

public class DomSemanticProvider implements SemanticProvider {
    @Override
    @SuppressWarnings("unchecked")
    public SemanticSignals extract(WebDriver driver, WebElement element) {
        if (!(driver instanceof JavascriptExecutor js) || element == null) {
            return SemanticSignals.empty("dom-unavailable");
        }

        Object raw = js.executeScript("""
            function trim(v) { return v ? String(v).trim() : ''; }
            function textOf(el) { return el && el.innerText ? el.innerText.replace(/\\s+/g, ' ').trim() : ''; }
            function attr(el, name) { return el ? trim(el.getAttribute(name)) : ''; }
            function referencedText(ids) {
              if (!ids) return '';
              var parts = [];
              ids.split(/\\s+/).forEach(function(id) {
                if (!id) return;
                var ref = document.getElementById(id);
                var txt = textOf(ref);
                if (txt) parts.push(txt);
              });
              return parts.join(' ');
            }
            function labelLikeText(el) {
              if (!el) return '';
              var aria = referencedText(attr(el, 'aria-labelledby'));
              if (aria) return aria;
              var direct = attr(el, 'aria-label') || attr(el, 'data-label');
              if (direct) return direct;
              var txt = textOf(el);
              if (!txt) return '';
              var tag = el.tagName.toLowerCase();
              var cls = attr(el, 'class').toLowerCase();
              if (tag === 'label' || tag === 'legend') return txt;
              if (cls.indexOf('label') >= 0 || cls.indexOf('title') >= 0) return txt;
              if (tag === 'span' || tag === 'div' || tag === 'p' || tag === 'strong') return txt;
              return '';
            }
            function isFieldLike(el) {
              if (!el) return false;
              var tag = el.tagName.toLowerCase();
              var type = attr(el, 'type').toLowerCase();
              var editable = attr(el, 'contenteditable').toLowerCase() === 'true';
              if (editable || tag === 'textarea' || tag === 'select') return true;
              if (tag === 'input') return type !== 'hidden';
              return false;
            }
            function nearestLabelText(el) {
              var parts = [];
              var labelledBy = referencedText(attr(el, 'aria-labelledby'));
              if (labelledBy) parts.push(labelledBy);
              var wrappedLabel = el ? el.closest('label') : null;
              var wrappedText = textOf(wrappedLabel);
              if (wrappedText) parts.push(wrappedText);
              if (el && el.id) {
                var byFor = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
                var byForText = textOf(byFor);
                if (byForText) parts.push(byForText);
              }
              if (parts.length) return parts[0];
              var prev = el ? el.previousElementSibling : null;
              while (prev) {
                var prevLabel = labelLikeText(prev);
                if (prevLabel) return prevLabel;
                if (isFieldLike(prev)) break;
                prev = prev.previousElementSibling;
              }
              return '';
            }
            function placeholderText(el) {
              return attr(el, 'placeholder') || attr(el, 'data-placeholder');
            }
            function descriptionText(el) {
              var parts = [];
              var describedBy = referencedText(attr(el, 'aria-describedby'));
              if (describedBy) parts.push(describedBy);
              var ariaDescription = attr(el, 'aria-description');
              if (ariaDescription) parts.push(ariaDescription);
              var title = attr(el, 'title');
              if (title) parts.push(title);
              var next = el ? el.nextElementSibling : null;
              while (next) {
                var txt = textOf(next);
                var cls = attr(next, 'class').toLowerCase();
                var tag = next.tagName.toLowerCase();
                if (txt && (tag === 'small' || tag === 'p' || cls.indexOf('hint') >= 0 || cls.indexOf('help') >= 0
                    || cls.indexOf('desc') >= 0 || cls.indexOf('note') >= 0 || cls.indexOf('helper') >= 0)) {
                  parts.push(txt);
                  break;
                }
                if (isFieldLike(next) || labelLikeText(next)) break;
                next = next.nextElementSibling;
              }
              return [...new Set(parts)].join(' | ');
            }
            function nearestSectionText(el) {
              var current = el ? el.parentElement : null;
              while (current) {
                for (var i = 0; i < current.children.length; i++) {
                  var child = current.children[i];
                  var tag = child.tagName.toLowerCase();
                  var cls = attr(child, 'class').toLowerCase();
                  if (tag === 'legend' || /^h[1-6]$/.test(tag) || cls.indexOf('title') >= 0
                      || cls.indexOf('heading') >= 0 || cls.indexOf('header') >= 0) {
                    var txt = textOf(child);
                    if (txt && txt.length <= 120) return txt;
                  }
                }
                current = current.parentElement;
              }
              return '';
            }
            function parentContext(el) {
              var current = el ? el.parentElement : null;
              var selfText = textOf(el);
              while (current) {
                var txt = textOf(current);
                if (txt) {
                  if (selfText) txt = txt.replace(selfText, ' ').replace(/\\s+/g, ' ').trim();
                  if (txt && txt.length <= 120) return txt;
                }
                var tag = current.tagName.toLowerCase();
                if (tag === 'form' || tag === 'body') break;
                current = current.parentElement;
              }
              return '';
            }
            function controlType(el) {
              if (!el) return '';
              var type = attr(el, 'type').toLowerCase();
              if (type) return type;
              if (attr(el, 'contenteditable').toLowerCase() === 'true') return 'contenteditable';
              return el.tagName.toLowerCase();
            }
            function computedRole(el) {
              var role = attr(el, 'role').toLowerCase();
              if (role) return role;
              if (!el) return '';
              var tag = el.tagName.toLowerCase();
              var type = attr(el, 'type').toLowerCase();
              var editable = attr(el, 'contenteditable').toLowerCase() === 'true';
              if (tag === 'button') return 'button';
              if (tag === 'a' && attr(el, 'href')) return 'link';
              if (tag === 'select') return 'combobox';
              if (tag === 'textarea' || editable) return 'textbox';
              if (tag === 'input') {
                if (type === 'checkbox') return 'checkbox';
                if (type === 'radio') return 'radio';
                if (type === 'button' || type === 'submit' || type === 'reset') return 'button';
                return 'textbox';
              }
              if (!!el.onclick) return 'button';
              var tabIndex = el.getAttribute('tabindex');
              if (tabIndex !== null && Number(tabIndex) >= 0 && textOf(el)) return 'button';
              return tag;
            }
            function accessibleName(el) {
              if (!el) return '';
              var labelledBy = referencedText(attr(el, 'aria-labelledby'));
              if (labelledBy) return labelledBy;
              var ariaLabel = attr(el, 'aria-label');
              if (ariaLabel) return ariaLabel;
              var label = nearestLabelText(el);
              if (label) return label;
              var title = attr(el, 'title');
              if (title) return title;
              var placeholder = placeholderText(el);
              if (placeholder) return placeholder;
              var value = attr(el, 'value');
              if (value && (computedRole(el) === 'button' || el.tagName.toLowerCase() === 'input')) return value;
              return textOf(el);
            }
            var el = arguments[0];
            return {
              accessibleName: accessibleName(el),
              semanticRole: computedRole(el),
              autocomplete: attr(el, 'autocomplete'),
              labelText: nearestLabelText(el),
              placeholder: placeholderText(el),
              descriptionText: descriptionText(el),
              sectionContext: nearestSectionText(el),
              parentContext: parentContext(el),
              inputType: controlType(el)
            };
            """, element);

        if (!(raw instanceof Map<?, ?> meta)) {
            return SemanticSignals.empty("dom");
        }

        return new SemanticSignals(
            str(meta.get("accessibleName")),
            str(meta.get("semanticRole")),
            str(meta.get("autocomplete")),
            str(meta.get("labelText")),
            str(meta.get("placeholder")),
            str(meta.get("descriptionText")),
            str(meta.get("sectionContext")),
            str(meta.get("parentContext")),
            str(meta.get("inputType")),
            "dom"
        );
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
