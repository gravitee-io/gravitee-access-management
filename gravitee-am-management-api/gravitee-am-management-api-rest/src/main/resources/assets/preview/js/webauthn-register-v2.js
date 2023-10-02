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

const registerForm = document.getElementById('register');
const registerButton = registerForm.querySelector('button, button#register-button');
const errorElement = document.getElementById('webauthn-error');
const endpoint = registerForm.action.replace('/webauthn/register', '/webauthn/register/credentials');

const displayMessage = message => {
    errorElement.getElementsByClassName('error_description')[0].innerHTML = message;
    errorElement.style.display = 'block';
};

const clearMessage = () => {
    errorElement.style.display = 'none';
};

function register(data) {
    var xhr = new XMLHttpRequest();
    xhr.open("POST", endpoint, true);
    xhr.setRequestHeader("Content-Type", "application/json; charset=utf-8");
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4) {
            if (xhr.status === 200) {
                // prepare credential options
                var makeCredentialOptions = JSON.parse(xhr.responseText);
                makeCredentialOptions.challenge = base64ToBuffer(makeCredentialOptions.challenge);
                makeCredentialOptions.user.id = base64ToBuffer(makeCredentialOptions.user.id);
                if (makeCredentialOptions.excludeCredentials) {
                    for (let i = 0; i < makeCredentialOptions.excludeCredentials.length; i++) {
                        makeCredentialOptions.excludeCredentials[i].id = base64ToBuffer(makeCredentialOptions.excludeCredentials[i].id);
                    }
                }
                // create credential
                navigator.credentials.create({publicKey: makeCredentialOptions})
                    .then(function (newCredential) {
                        var res = JSON.stringify({
                            id: newCredential.id,
                            rawId: bufferToBase64(newCredential.rawId),
                            response: {
                                attestationObject: bufferToBase64(newCredential.response.attestationObject),
                                clientDataJSON: bufferToBase64(newCredential.response.clientDataJSON)
                            },
                            type: newCredential.type
                        });
                        clearMessage();
                        // insert value as hidden field and submit the form
                        var input = document.createElement("input");
                        input.setAttribute("type", "hidden");
                        input.setAttribute("name", "assertion");
                        input.setAttribute("value", res);
                        registerForm.appendChild(input);
                        registerForm.submit();
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

registerButton.addEventListener('click', function(event){
    event.preventDefault();
    register({
        name: registerForm.username.value,
        displayName: registerForm.username.value
    });
});
