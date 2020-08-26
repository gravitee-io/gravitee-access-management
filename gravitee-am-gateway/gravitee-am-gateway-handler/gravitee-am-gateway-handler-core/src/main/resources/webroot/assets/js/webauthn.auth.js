'use strict';

/**
 * Converts PublicKeyCredential into serialised JSON
 * @param  {Object} pubKeyCred
 * @return {Object}            - JSON encoded publicKeyCredential
 */
let publicKeyCredentialToJSON = (pubKeyCred) => {
    if (pubKeyCred instanceof Array) {
        let arr = [];
        for (let i of pubKeyCred) { arr.push(publicKeyCredentialToJSON(i)) }

        return arr
    }

    if (pubKeyCred instanceof ArrayBuffer) {
        return base64url.encode(pubKeyCred)
    }

    if (pubKeyCred instanceof Object) {
        let obj = {};

        for (let key in pubKeyCred) {
            obj[key] = publicKeyCredentialToJSON(pubKeyCred[key])
        }

        return obj
    }

    return pubKeyCred
};

/**
 * Generate secure random buffer
 * @param  {Number} len - Length of the buffer (default 32 bytes)
 * @return {Uint8Array} - random string
 */
let generateRandomBuffer = (len) => {
    len = len || 32;

    let randomBuffer = new Uint8Array(len);
    window.crypto.getRandomValues(randomBuffer);

    return randomBuffer
};

/**
 * Decodes arrayBuffer required fields.
 */
let preformatMakeCredReq = (makeCredReq) => {
    makeCredReq.challenge = base64url.decode(makeCredReq.challenge);
    makeCredReq.user.id = base64url.decode(makeCredReq.user.id);

    return makeCredReq
};

/**
 * Decodes arrayBuffer required fields.
 */
let preformatGetAssertReq = (getAssert) => {
    getAssert.challenge = base64url.decode(getAssert.challenge);

    for (let allowCred of getAssert.allowCredentials) {
        allowCred.id = base64url.decode(allowCred.id)
    }

    return getAssert
};

let httpCall = (url, formBody) => {
    return fetch(url, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(formBody)
    })
    .then(response => {
        if (!response.ok) {
            throw new Error(`Server responded with error: ${response.statusText}`);
        }
        return response;
    });
}

let getMakeCredentialsChallenge = (url, formBody) => {
    return httpCall(url, formBody)
        .then((response) => response.json())
};

let sendWebAuthnResponse = (url, formBody) => {
    return httpCall(url, formBody);
};

let getGetAssertionChallenge = (url, formBody) => {
    return httpCall(url, formBody)
        .then((response) => response.json())
};

let register = (url, formBody) => {
    return getMakeCredentialsChallenge(url, formBody)
        .then((response) => {
            let publicKey = preformatMakeCredReq(response);
            clearAlert();
            return navigator.credentials.create({publicKey})
        });
};

let login = (url, formBody) => {
    return getGetAssertionChallenge(url, formBody)
        .then((response) => {
            let publicKey = preformatGetAssertReq(response);
            clearAlert();
            return navigator.credentials.get({publicKey})
        })
};

let clearAlert = () => {
    if (document.getElementById('webauthn-error')) {
        document.getElementById('webauthn-error').style.display = 'none';
    }
};

/* Handle for register form submission */
if (document.getElementById('register')) {
    document.getElementById('register').addEventListener('submit', function (event) {
        event.preventDefault();

        let registerURL = this.action;
        let responseURL = registerURL.replace('/webauthn/register', '/webauthn/response');
        let name = this.username.value;
        let displayName = this.username.value;

        if (!name || !displayName) {
            window.alert('DisplayName or username is missing!');
            return
        }

        register(registerURL, {name, displayName})
            .then((response) => {
                let makeCredResponse = publicKeyCredentialToJSON(response);
                return sendWebAuthnResponse(responseURL, makeCredResponse)
            })
            .then((response) => {
                window.location.replace(response.headers.get('Location'));
            })
            .catch((error) => {
                if (document.getElementById('webauthn-error')) {
                    document.getElementById('webauthn-error').style.display = 'block'
                }
            });
    });
}

/* Handle for login form submission */
if (document.getElementById('login')) {
    document.getElementById('login').addEventListener('submit', function (event) {
        event.preventDefault();

        let loginURL = this.action;
        let responseURL = loginURL.replace('/webauthn/login', '/webauthn/response');
        let name = this.username.value;

        if (!name) {
            window.alert('Username is missing!');
            return
        }

        login(loginURL, {name})
            .then((response) => {
                let getAssertionResponse = publicKeyCredentialToJSON(response);
                return sendWebAuthnResponse(responseURL, getAssertionResponse)
            })
            .then((response) => {
                window.location.replace(response.headers.get('Location'));
            })
            .catch((error) => {
                if (document.getElementById('webauthn-error')) {
                    document.getElementById('webauthn-error').style.display = 'block'
                }
            });
    });
}

/* Check if current browser supports WebAuthn */
document.addEventListener('DOMContentLoaded', async function () {
    if (window.PublicKeyCredential === undefined ||
        typeof window.PublicKeyCredential !== "function") {
        let errorMessage = "This browser doesn't currently support WebAuthn."
        if (window.location.protocol === "http:" && (window.location.hostname !== "localhost" && window.location.hostname !== "127.0.0.1")){
            errorMessage = "WebAuthn only supports secure connections. For testing over HTTP, you can use the origin \"localhost\"."
        }
        if (document.getElementById('webauthn-browser-error')
            && document.getElementById('webauthn-browser-error-description')) {
            document.getElementById('webauthn-browser-error').style.display = 'block';
            document.getElementById('webauthn-browser-error-description').innerHTML = errorMessage;
        }
        return;
    }
});

/*
 * Base64URL-ArrayBuffer
 * https://github.com/herrjemand/Base64URL-ArrayBuffer
 *
 * Copyright (c) 2017 Yuriy Ackermann <ackermann.yuriy@gmail.com>
 * Copyright (c) 2012 Niklas von Hertzen
 * Licensed under the MIT license.
 *
 */
(function () {
    'use strict'

    let chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'

    // Use a lookup table to find the index.
    let lookup = new Uint8Array(256)
    for (let i = 0; i < chars.length; i++) {
        lookup[chars.charCodeAt(i)] = i
    }

    let encode = function (arraybuffer) {
        let bytes = new Uint8Array(arraybuffer)

        let i; let len = bytes.length; let base64url = ''

        for (i = 0; i < len; i += 3) {
            base64url += chars[bytes[i] >> 2]
            base64url += chars[((bytes[i] & 3) << 4) | (bytes[i + 1] >> 4)]
            base64url += chars[((bytes[i + 1] & 15) << 2) | (bytes[i + 2] >> 6)]
            base64url += chars[bytes[i + 2] & 63]
        }

        if ((len % 3) === 2) {
            base64url = base64url.substring(0, base64url.length - 1)
        } else if (len % 3 === 1) {
            base64url = base64url.substring(0, base64url.length - 2)
        }

        return base64url
    }

    let decode = function (base64string) {
        let bufferLength = base64string.length * 0.75

        let len = base64string.length; let i; let p = 0

        let encoded1; let encoded2; let encoded3; let encoded4

        let bytes = new Uint8Array(bufferLength)

        for (i = 0; i < len; i += 4) {
            encoded1 = lookup[base64string.charCodeAt(i)]
            encoded2 = lookup[base64string.charCodeAt(i + 1)]
            encoded3 = lookup[base64string.charCodeAt(i + 2)]
            encoded4 = lookup[base64string.charCodeAt(i + 3)]

            bytes[p++] = (encoded1 << 2) | (encoded2 >> 4)
            bytes[p++] = ((encoded2 & 15) << 4) | (encoded3 >> 2)
            bytes[p++] = ((encoded3 & 3) << 6) | (encoded4 & 63)
        }

        return bytes.buffer
    }

    let methods = {
        'decode': decode,
        'encode': encode
    }

    /**
     * Exporting and stuff
     */
    if (typeof module !== 'undefined' && typeof module.exports !== 'undefined') {
        module.exports = methods
    } else {
        if (typeof define === 'function' && define.amd) {
            define([], function () {
                return methods
            })
        } else {
            window.base64url = methods
        }
    }
})();
