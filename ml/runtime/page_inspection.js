/**
 * Page inspection: run in the app's WebView (core/pagecontent PageContentJudge) and
 * in the collector's headless Chromium (ml/guadian_ml/llm/dataset/collect.py), so
 * the model is trained on what the app reads. Returns what PageContentJudge's own
 * verdict and the LLM's PageObservation need, not the full DOM. Text joins every
 * text node outside script/style/noscript with spaces, as BeautifulSoup's
 * get_text(" ") does; forms, fields, and APK links use the original collector's
 * selectors and limits (ml/guadian_ml/llm/vendor/judge_agent.py). `shown` marks a
 * field the user can see now: not type=hidden, not display:none or
 * visibility:hidden, and with a non-empty box. hasPasswordField counts shown
 * password fields only, so a password box in a closed popup is not a login form.
 */
(function() {
    try {
        var skip = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1 };
        var parts = [], size = 0;
        var walker = document.createTreeWalker(document.documentElement, NodeFilter.SHOW_TEXT, null);
        for (var node = walker.nextNode(); node && size < 2000000; node = walker.nextNode()) {
            var inside = false;
            for (var p = node.parentNode; p; p = p.parentNode) { if (skip[p.nodeName]) { inside = true; break; } }
            if (inside) continue;
            parts.push(node.nodeValue);
            size += node.nodeValue.length;
        }
        var resolve = function(href) { try { return new URL(href, location.href).href; } catch (e) { return href; } };
        var shown = function(el) {
            if ((el.getAttribute('type') || '').toLowerCase() === 'hidden') return false;
            var style = window.getComputedStyle(el);
            if (style.display === 'none' || style.visibility === 'hidden') return false;
            var box = el.getBoundingClientRect();
            return box.width > 0 && box.height > 0;
        };
        var forms = Array.prototype.slice.call(document.forms, 0, 6).map(function(form) {
            var action = form.getAttribute('action') || '';
            var controls = form.querySelectorAll('input, select, textarea');
            return {
                action: action ? resolve(action) : null,
                fields: Array.prototype.slice.call(controls, 0, 20).map(function(el) {
                    return {
                        type: (el.getAttribute('type') || el.tagName || '').toLowerCase(),
                        name: el.getAttribute('name'),
                        id: el.getAttribute('id'),
                        placeholder: el.getAttribute('placeholder'),
                        autocomplete: el.getAttribute('autocomplete'),
                        shown: shown(el)
                    };
                })
            };
        });
        var apkAnchors = [];
        document.querySelectorAll('a[href]').forEach(function(a) {
            var href = a.getAttribute('href');
            if (/\.apk(\?|$)/i.test(href)) apkAnchors.push(resolve(href));
        });
        var titleEl = document.querySelector('title');
        /*
         * Old libraries (Prototype.js) define Array.prototype.toJSON, which makes
         * JSON.stringify write arrays as strings; set it aside while serializing.
         */
        var arrayToJSON = Array.prototype.toJSON;
        if (arrayToJSON) delete Array.prototype.toJSON;
        try {
            return JSON.stringify({
                hasPasswordField: Array.prototype.some.call(document.querySelectorAll('input[type="password"]'), shown),
                title: titleEl ? titleEl.textContent : '',
                bodyText: (document.body ? document.body.innerText : '').slice(0, 2000),
                text: parts.join(' '),
                forms: forms,
                apkAnchors: apkAnchors,
                apkInHtml: document.documentElement.outerHTML.match(/https?:\/\/[^\s"'<>]+\.apk\b/gi) || []
            });
        } finally {
            if (arrayToJSON) Array.prototype.toJSON = arrayToJSON;
        }
    } catch (e) {
        return JSON.stringify({ error: String(e) });
    }
})()
