package com.visual.semantic;

public final class BrowserSemanticScripts {
    private BrowserSemanticScripts() {
    }

    private static final String COMMON_HELPERS = """
        function trim(v) { return v ? String(v).trim() : ''; }
        function textOf(el) { return el && el.innerText ? el.innerText.replace(/\\s+/g, ' ').trim() : ''; }
        function attr(el, name) { return el ? trim(el.getAttribute(name)) : ''; }
        function cssEscape(value) {
          if (window.CSS && CSS.escape) return CSS.escape(value);
          return String(value).replace(/[^a-zA-Z0-9_-]/g, function(ch) { return '\\\\' + ch; });
        }
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
          if (cls.indexOf('label') >= 0 || cls.indexOf('title') >= 0 || cls.indexOf('heading') >= 0
              || cls.indexOf('caption') >= 0 || cls.indexOf('eyebrow') >= 0) return txt;
          if (/^h[1-6]$/.test(tag)) return txt;
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
        function basicLabelTexts(el) {
          var parts = [];
          var labelledBy = referencedText(attr(el, 'aria-labelledby'));
          if (labelledBy) parts.push(labelledBy);
          var wrappedLabel = el ? el.closest('label') : null;
          var wrappedText = textOf(wrappedLabel);
          if (wrappedText) parts.push(wrappedText);
          if (el && el.id) {
            var byFor = document.querySelector('label[for="' + cssEscape(el.id) + '"]');
            var byForText = textOf(byFor);
            if (byForText) parts.push(byForText);
          }
          var prev = el ? el.previousElementSibling : null;
          while (prev) {
            var prevText = labelLikeText(prev);
            if (prevText) {
              parts.push(prevText);
              break;
            }
            if (isFieldLike(prev)) break;
            prev = prev.previousElementSibling;
          }
          return [...new Set(parts.filter(function(part) { return !!trim(part); }))];
        }
        function contextualLabelTexts(el) {
          var parts = basicLabelTexts(el);
          var current = el;
          while (current && current.parentElement) {
            var parent = current.parentElement;
            var labels = parent.querySelectorAll(':scope > label, :scope > legend, :scope > .e-title, :scope > .caption, :scope > .eyebrow, :scope > [data-label], :scope > [aria-label]');
            for (var i = labels.length - 1; i >= 0; i--) {
              if (labels[i] === current || labels[i].contains(current)) continue;
              var txt = textOf(labels[i]) || attr(labels[i], 'aria-label') || attr(labels[i], 'data-label');
              if (txt) {
                parts.push(txt);
                break;
              }
            }
            current = parent;
          }
          return [...new Set(parts.filter(function(part) { return !!trim(part); }))];
        }
        function primaryBasicLabelText(el) {
          var labels = basicLabelTexts(el);
          return labels.length ? labels[0] : '';
        }
        function primaryLabelText(el) {
          return primaryBasicLabelText(el);
        }
        function joinedBasicLabelText(el) {
          return basicLabelTexts(el).join(' | ');
        }
        function primaryContextualLabelText(el) {
          var labels = contextualLabelTexts(el);
          return labels.length ? labels[0] : '';
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
          return [...new Set(parts.filter(function(part) { return !!trim(part); }))].join(' | ');
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
        function selectHint(el) {
          var role = attr(el, 'role').toLowerCase();
          if (role === 'combobox' || role === 'listbox') return true;
          if (attr(el, 'aria-haspopup').toLowerCase() === 'listbox') return true;
          var text = [attr(el, 'aria-label'), placeholderText(el), joinedBasicLabelText(el), textOf(el)].join(' ').toLowerCase();
          if (!text) return false;
          return /(\\bchoose\\b|\\bselect\\b|\\bpick\\b)/.test(text)
              || /(\\bcountry\\b|\\bnation\\b|\\bregion\\b|\\blocation\\b)/.test(text);
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
        function computedRoleWithSelectHint(el) {
          var role = computedRole(el);
          if (role === 'textbox' && selectHint(el)) return 'combobox';
          return role;
        }
        function accessibleName(el, labelText, roleValue) {
          if (!el) return '';
          var labelledBy = referencedText(attr(el, 'aria-labelledby'));
          if (labelledBy) return labelledBy;
          var ariaLabel = attr(el, 'aria-label');
          if (ariaLabel) return ariaLabel;
          var label = labelText || primaryLabelText(el);
          if (label) return label;
          var title = attr(el, 'title');
          if (title) return title;
          var placeholder = placeholderText(el);
          if (placeholder) return placeholder;
          var value = attr(el, 'value');
          if (value && ((roleValue || computedRole(el)) === 'button' || el.tagName.toLowerCase() === 'input')) return value;
          return textOf(el);
        }
        function isClickable(el) {
          if (!el) return false;
          var tag = el.tagName.toLowerCase();
          var role = computedRole(el);
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
        function normalizeMeaningfulElement(el) {
          var current = el;
          while (current) {
            if (isMeaningful(current)) return current;
            if (!current.parentElement) return current;
            current = current.parentElement;
          }
          return el;
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
        function classify(el) {
          var tag = el.tagName.toLowerCase();
          var type = attr(el, 'type').toLowerCase();
          var role = computedRole(el);
          var editable = attr(el, 'contenteditable').toLowerCase() === 'true';
          if (tag === 'select') return 'select';
          if (role === 'checkbox' || role === 'switch' || role === 'radio') return 'toggle';
          if (el.classList.contains('fake-toggle') || el.querySelector('.toggle-box')) return 'toggle';
          if (tag === 'button' || tag === 'a' || role === 'button' || role === 'link') return 'action';
          if (selectHint(el)) return 'select';
          if (tag === 'textarea' || editable) return 'text';
          if (tag === 'input') {
            if (type === 'checkbox' || type === 'radio') return 'toggle';
            if (type === 'button' || type === 'submit' || type === 'reset') return 'action';
            return 'text';
          }
          if (!!el.onclick) return 'action';
          return 'generic';
        }
        function getCssPath(el) {
          if (!(el instanceof Element)) return '';
          var path = [];
          while (el && el.nodeType === Node.ELEMENT_NODE) {
            var selector = el.nodeName.toLowerCase();
            if (el.id) {
              selector += '#' + el.id;
              path.unshift(selector);
              break;
            }
            var sib = el, nth = 1;
            while ((sib = sib.previousElementSibling)) { if (sib.nodeName.toLowerCase() === selector) nth++; }
            if (nth !== 1) selector += ':nth-of-type(' + nth + ')';
            var className = (el.className || '').trim();
            if (className) selector += '.' + className.split(/\\s+/).join('.');
            path.unshift(selector);
            el = el.parentElement;
          }
          return path.join(' > ');
        }
        function elementText(el, labelText) {
          var parts = [];
          var effectiveLabel = labelText || joinedBasicLabelText(el);
          var accessible = accessibleName(el, effectiveLabel, computedRole(el));
          if (el) parts.push(el.value);
          parts.push(placeholderText(el));
          parts.push(attr(el, 'aria-label'));
          parts.push(accessible);
          parts.push(textOf(el));
          parts.push(effectiveLabel);
          return [...new Set(parts.filter(function(part) { return !!trim(part); }))].join(' | ').substring(0, 120);
        }
        """;

    public static String domSemanticExtractionScript() {
        return COMMON_HELPERS + """
            var el = arguments[0];
            var labelText = primaryContextualLabelText(el) || primaryBasicLabelText(el);
            var role = computedRole(el);
            return {
              accessibleName: accessibleName(el, labelText, role),
              semanticRole: role,
              autocomplete: attr(el, 'autocomplete'),
              labelText: labelText,
              placeholder: placeholderText(el),
              descriptionText: descriptionText(el),
              sectionContext: nearestSectionText(el),
              parentContext: parentContext(el),
              inputType: controlType(el)
            };
        """;
    }

    public static String pageIdentityScript() {
        return COMMON_HELPERS + """
            function collectPageIdentityTexts() {
              var values = [];
              function pushText(text) {
                var normalized = trim(text).replace(/\\s+/g, ' ');
                if (!normalized) return;
                if (values.indexOf(normalized) >= 0) return;
                values.push(normalized);
              }
              pushText(document.title || '');
              document.querySelectorAll('h1, h2, h3, legend, label, .e-title, [data-section-title], [data-label]').forEach(function(node) {
                pushText(textOf(node) || attr(node, 'data-label'));
              });
              return values.slice(0, 18);
            }
            function collectHeadingTexts() {
              var values = [];
              function pushText(text) {
                var normalized = trim(text).replace(/\\s+/g, ' ');
                if (!normalized) return;
                if (values.indexOf(normalized) >= 0) return;
                values.push(normalized);
              }
              document.querySelectorAll('h1, h2, h3, legend, .e-title, [data-section-title], [role="heading"]').forEach(function(node) {
                pushText(textOf(node) || attr(node, 'data-section-title'));
              });
              return values.slice(0, 12);
            }
            function collectFormTexts() {
              var values = [];
              function pushText(text) {
                var normalized = trim(text).replace(/\\s+/g, ' ');
                if (!normalized) return;
                if (values.indexOf(normalized) >= 0) return;
                values.push(normalized);
              }
              document.querySelectorAll('label, input, textarea, select, [contenteditable="true"], [role="textbox"], [role="combobox"], [data-label]').forEach(function(node) {
                if (node.matches('input, textarea, select, [contenteditable="true"], [role="textbox"], [role="combobox"]')) {
                  pushText(primaryContextualLabelText(node) || primaryBasicLabelText(node));
                  pushText(placeholderText(node));
                  return;
                }
                pushText(textOf(node) || attr(node, 'data-label'));
              });
              return values.slice(0, 18);
            }
            function normalizedPath() {
              var path = trim(window.location && window.location.pathname ? window.location.pathname : '');
              return path.toLowerCase();
            }
            var texts = collectPageIdentityTexts();
            var headings = collectHeadingTexts();
            var formTexts = collectFormTexts();
            return {
              pageTitle: trim(document.title || ''),
              pageFingerprint: texts.join(' | '),
              normalizedPath: normalizedPath(),
              headingFingerprint: headings.join(' | '),
              formFingerprint: formTexts.join(' | ')
            };
        """;
    }

    public static String locatorExtractionScript(String startExpression) {
        return locatorExtractionScript(startExpression, true);
    }

    public static String locatorExtractionScript(String startExpression, boolean normalizeStart) {
        String targetExpression = normalizeStart ? "normalizeMeaningfulElement(start)" : "start";
        return (COMMON_HELPERS + """
            var start = %s;
            if (!start) return null;
            var target = %s;
            var ancestor = nearestStableAncestor(target);
            var labelText = primaryContextualLabelText(target);
            var role = computedRole(target);
            return [target, {
              tagName: target.tagName.toLowerCase(),
              id: attr(target, 'id'),
              name: attr(target, 'name'),
              className: attr(target, 'class'),
              dataTestId: attr(target, 'data-testid'),
              dataTest: attr(target, 'data-test'),
              ariaLabel: attr(target, 'aria-label'),
              placeholder: placeholderText(target),
              accessibleName: accessibleName(target, labelText, role),
              type: attr(target, 'type'),
              text: textOf(target),
              labelText: labelText,
              parentText: textOf(target.parentElement),
              role: role,
              autocomplete: attr(target, 'autocomplete'),
              contentEditable: attr(target, 'contenteditable'),
              ancestorId: ancestor.id,
              ancestorDataTestId: ancestor.dataTestId,
              ancestorDataTest: ancestor.dataTest,
              ancestorClassName: ancestor.className,
              ancestorTagName: ancestor.tagName
            }];
            """).formatted(startExpression, targetExpression);
    }

    public static String visualCandidateCollectionScript() {
        return COMMON_HELPERS + """
            var sel='input,select,button,textarea,a,[role],[tabindex],[contenteditable=true]';
            var els=document.querySelectorAll(sel);
            window.__visualCandidates=[];
            var out=[];
            for(var i=0;i<els.length;i++){
              var e=els[i], r=e.getBoundingClientRect();
              var sx=Math.round(window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0);
              var sy=Math.round(window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0);
              if(r.width<=0||r.height<=0) continue;
              var idx=window.__visualCandidates.length;
              var labelText = primaryContextualLabelText(e) || joinedBasicLabelText(e);
              var role = computedRole(e);
              window.__visualCandidates.push(e);
              out.push({
                x:Math.round(r.left)+sx,
                y:Math.round(r.top)+sy,
                w:Math.round(r.width),
                h:Math.round(r.height),
                text:elementText(e, labelText),
                idx:idx,
                selector:getCssPath(e),
                kind:classify(e),
                accessibleName:accessibleName(e, labelText, role),
                semanticRole:role,
                autocomplete:attr(e, 'autocomplete'),
                tag:e.tagName.toLowerCase()
              });
            }
            return out;
            """;
    }

    public static String visualMetadataScript() {
        return COMMON_HELPERS + """
            var e=arguments[0], r=e.getBoundingClientRect(), parts=[];
            var sx=Math.round(window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0);
            var sy=Math.round(window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0);
            var labelText = primaryContextualLabelText(e) || joinedBasicLabelText(e);
            var role = computedRoleWithSelectHint(e);
            return {
              x:Math.round(r.left)+sx, y:Math.round(r.top)+sy, w:Math.round(r.width), h:Math.round(r.height),
              text:elementText(e, labelText),
              kind:classify(e), tag:e.tagName.toLowerCase(),
              accessibleName:accessibleName(e, labelText, role), semanticRole:role, autocomplete:attr(e, 'autocomplete')
            };
            """;
    }
}
