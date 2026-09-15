'use strict';

/**
 * Same-origin WebAuthn client error telemetry (v4). Loaded only with webauthn-*-v4.js scripts.
 * Prefers fetch+keepalive when available; falls back to XMLHttpRequest for older runtimes that still support WebAuthn.
 * The gateway validates Origin and CSRF (X-XSRF-TOKEN header + XSRF-TOKEN cookie). Pass the surrounding form so the script can forward the token.
 */
(function (global) {
    var TELEMETRY_SUFFIX = '/webauthn/webauthn-error';
    var CSRF_HEADER = 'X-XSRF-TOKEN';

    function truncate(str, max) {
        if (!str) {
            return '';
        }
        return str.length <= max ? str : str.substring(0, max);
    }

    function readXsrfTokenFromForm(form) {
        if (!form || !form.querySelector) {
            return null;
        }
        var el = form.querySelector('input[name="' + CSRF_HEADER + '"]');
        return el && el.value ? el.value : null;
    }

    function postJsonFireAndForget(url, body, xsrfToken) {
        var headers = { 'Content-Type': 'application/json' };
        if (xsrfToken) {
            headers[CSRF_HEADER] = xsrfToken;
        }
        if (global.fetch) {
            try {
                global.fetch(url, {
                    method: 'POST',
                    credentials: 'same-origin',
                    keepalive: true,
                    headers: headers,
                    body: body
                }).catch(function () { /* ignore */ });
                return;
            } catch (e) {
                /* fall through to XHR */
            }
        }
        try {
            var xhr = new global.XMLHttpRequest();
            xhr.open('POST', url, true);
            xhr.setRequestHeader('Content-Type', 'application/json; charset=utf-8');
            if (xsrfToken) {
                xhr.setRequestHeader(CSRF_HEADER, xsrfToken);
            }
            xhr.send(body);
        } catch (e2) {
            /* ignore */
        }
    }

    global.resolveWebauthnErrorUrlFromForm = function (form) {
        if (!form || !form.action) {
            return null;
        }
        var action = form.action;
        if (action.indexOf('/webauthn/login') !== -1) {
            return action.replace('/webauthn/login', TELEMETRY_SUFFIX);
        }
        if (action.indexOf('/webauthn/register') !== -1) {
            return action.replace('/webauthn/register', TELEMETRY_SUFFIX);
        }
        return null;
    };

    /**
     * @param {string} reportUrl absolute URL for POST /webauthn/webauthn-error
     * @param {string} phase login | register | mfa
     * @param {string} operation get | create
     * @param {*} err Error or DOMException
     * @param {string|null} rpId
     * @param {string|null|undefined} username optional (e.g. login field or MFA name)
     * @param {HTMLFormElement|undefined} formForCsrf form that contains the hidden CSRF input
     */
    global.reportWebauthnClientError = function (reportUrl, phase, operation, err, rpId, username, formForCsrf) {
        if (!reportUrl) {
            return;
        }
        var errorName = err && err.name ? String(err.name) : 'Error';
        var errorMessage = err && err.message ? truncate(String(err.message), 500) : '';
        var payload = {
            phase: phase,
            operation: operation,
            errorName: errorName,
            errorMessage: errorMessage,
            clientTimestamp: Date.now(),
            rpId: rpId || null
        };
        if (username != null && String(username) !== '') {
            payload.username = truncate(String(username), 256);
        }
        var xsrf = formForCsrf ? readXsrfTokenFromForm(formForCsrf) : null;
        postJsonFireAndForget(reportUrl, JSON.stringify(payload), xsrf);
    };
}(typeof window !== 'undefined' ? window : this));
