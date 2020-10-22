'use strict';

let clearAlert = () => {
    if (document.getElementById('webauthn-error')) {
        document.getElementById('webauthn-error').style.display = 'none';
    }
};

/* Handle for register form submission */
if (document.getElementById('register')) {
    document.getElementById('register').addEventListener('submit', function (event) {
        event.preventDefault();
        const registerURL = this.action;
        const responseURL = registerURL.replace('/webauthn/register', '/webauthn/response');
        const name = this.username.value;
        const displayName = this.username.value;

        if (!name || !displayName) {
            window.alert('DisplayName or username is missing!');
            return
        }

        return fetch(registerURL, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({name, displayName})
        })
            .then(res => {
                if (res.status === 200) {
                    return res;
                }
                throw new Error(res.statusText);
            })
            .then(res => res.json())
            .then(res => {
                res.challenge = base64url.decode(res.challenge);
                res.user.id = base64url.decode(res.user.id);
                if (res.excludeCredentials) {
                    for (let i = 0; i < res.excludeCredentials.length; i++) {
                        res.excludeCredentials[i].id = base64url.decode(res.excludeCredentials[i].id);
                    }
                }
                clearAlert();
                return res;
            })
            .then(res => navigator.credentials.create({publicKey: res}))
            .then(credential => {
                return fetch(responseURL, {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        id: credential.id,
                        rawId: base64url.encode(credential.rawId),
                        response: {
                            attestationObject: base64url.encode(credential.response.attestationObject),
                            clientDataJSON: base64url.encode(credential.response.clientDataJSON)
                        },
                        type: credential.type
                    }),
                })
            })
            .then(res => {
                if (res.status >= 200 && res.status < 300) {
                    window.location.replace(res.headers.get('Location'));
                } else {
                    throw new Error(res.statusText);
                }
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
        const loginURL = this.action;
        const responseURL = loginURL.replace('/webauthn/login', '/webauthn/response');
        const name = this.username.value;

        if (!name) {
            window.alert('Username is missing!');
            return
        }

        return fetch(loginURL, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({name})
        })
            .then(res => {
                if (res.status === 200) {
                    return res;
                }
                throw new Error(res.statusText);
            })
            .then(res => res.json())
            .then(res => {
                res.challenge = base64url.decode(res.challenge);
                if (res.allowCredentials) {
                    for (let i = 0; i < res.allowCredentials.length; i++) {
                        res.allowCredentials[i].id = base64url.decode(res.allowCredentials[i].id);
                    }
                }
                clearAlert();
                return res;
            })
            .then(res => navigator.credentials.get({publicKey: res}))
            .then(credential => {
                return fetch(responseURL, {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        id: credential.id,
                        rawId: base64url.encode(credential.rawId),
                        response: {
                            clientDataJSON: base64url.encode(credential.response.clientDataJSON),
                            authenticatorData: base64url.encode(credential.response.authenticatorData),
                            signature: base64url.encode(credential.response.signature),
                            userHandle: base64url.encode(credential.response.userHandle),
                        },
                        type: credential.type
                    }),
                })
            })
            .then(res => {
                if (res.status >= 200 && res.status < 300) {
                    window.location.replace(res.headers.get('Location'));
                } else {
                    throw new Error(res.statusText);
                }
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

    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
    // Use a lookup table to find the index.
    const lookup = new Uint8Array(256);

    for (let i = 0; i < chars.length; i++) {
        lookup[chars.charCodeAt(i)] = i;
    }

    const encode = function (arraybuffer) {
        const bytes = new Uint8Array(arraybuffer);

        let i;
        let len = bytes.length;
        let base64url = '';

        for (i = 0; i < len; i += 3) {
            base64url += chars[bytes[i] >> 2];
            base64url += chars[((bytes[i] & 3) << 4) | (bytes[i + 1] >> 4)];
            base64url += chars[((bytes[i + 1] & 15) << 2) | (bytes[i + 2] >> 6)];
            base64url += chars[bytes[i + 2] & 63];
        }

        if ((len % 3) === 2) {
            base64url = base64url.substring(0, base64url.length - 1);
        } else if (len % 3 === 1) {
            base64url = base64url.substring(0, base64url.length - 2);
        }

        return base64url;
    }

    const decode = function (base64string) {
        if (base64string) {

            let bufferLength = base64string.length * 0.75;

            let len = base64string.length;
            let i;
            let p = 0;

            let encoded1;
            let encoded2;
            let encoded3;
            let encoded4;

            let bytes = new Uint8Array(bufferLength);

            for (i = 0; i < len; i += 4) {
                encoded1 = lookup[base64string.charCodeAt(i)];
                encoded2 = lookup[base64string.charCodeAt(i + 1)];
                encoded3 = lookup[base64string.charCodeAt(i + 2)];
                encoded4 = lookup[base64string.charCodeAt(i + 3)];

                bytes[p++] = (encoded1 << 2) | (encoded2 >> 4);
                bytes[p++] = ((encoded2 & 15) << 4) | (encoded3 >> 2);
                bytes[p++] = ((encoded3 & 3) << 6) | (encoded4 & 63);
            }

            return bytes.buffer;
        }
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
