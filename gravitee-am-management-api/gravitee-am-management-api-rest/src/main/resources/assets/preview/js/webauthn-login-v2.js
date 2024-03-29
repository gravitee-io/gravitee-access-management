/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

const loginForm = document.getElementById('login');
const loginButton = loginForm.querySelector('button, button#login-button');
const errorElement = document.getElementById('webauthn-error');
const endpoint = loginForm.action.replace('/webauthn/login', '/webauthn/login/credentials');

const displayMessage = message => {
    errorElement.getElementsByClassName('error_description')[0].innerHTML = message;
    errorElement.style.display = 'block';
};

const clearMessage = () => {
    errorElement.style.display = 'none';
};

function login(data) {
    var xhr = new XMLHttpRequest();
    xhr.open("POST", endpoint, true);
    xhr.setRequestHeader("Content-Type", "application/json; charset=utf-8");
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4) {
            if (xhr.status === 200) {
                var makeAssertionOptions = JSON.parse(xhr.responseText);
                makeAssertionOptions.challenge = base64ToBuffer(makeAssertionOptions.challenge);
                if (makeAssertionOptions.allowCredentials) {
                    for (let i = 0; i < makeAssertionOptions.allowCredentials.length; i++) {
                        makeAssertionOptions.allowCredentials[i].id = base64ToBuffer(makeAssertionOptions.allowCredentials[i].id);
                    }
                }
                navigator.credentials.get({publicKey: makeAssertionOptions})
                    .then(function (credential) {
                        var res = JSON.stringify({
                            id: credential.id,
                            rawId: bufferToBase64(credential.rawId),
                            response: {
                                clientDataJSON: bufferToBase64(credential.response.clientDataJSON),
                                authenticatorData: bufferToBase64(credential.response.authenticatorData),
                                signature: bufferToBase64(credential.response.signature),
                                userHandle: bufferToBase64(credential.response.userHandle),
                            },
                            type: credential.type
                        });
                        clearMessage();
                        // insert value as hidden field and submit the form
                        var input = document.createElement("input");
                        input.setAttribute("type", "hidden");
                        input.setAttribute("name", "assertion");
                        input.setAttribute("value", res);
                        loginForm.appendChild(input);
                        loginForm.submit();

                    })
                    .catch(function (err) {
                        displayMessage(err instanceof DOMException ? err.message : 'Invalid user');
                    });
            } else {
                displayMessage('Invalid request');
            }
        }
    };
    xhr.send(JSON.stringify(data || {}));
}

loginButton.addEventListener('click', function(event) {
    event.preventDefault();
    login({
        name: loginForm.username.value
    });
});
