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
const errorElement = document.getElementById('webauthn-error');

const w = new WebAuthn({
    registerPath: registerForm.action,
    callbackPath: registerForm.action.replace('/webauthn/register', '/webauthn/response')
});

const displayMessage = message => {
    errorElement.getElementsByClassName('error_description')[0].innerHTML = message;
    errorElement.style.display = 'block';
};

const clearMessage = () => {
    errorElement.style.display = 'none';
};

registerForm.onsubmit = () => {
    w
        .register({
            name: this.username.value,
            displayName: this.username.value
        })
        .then(res => {
            clearMessage();
            setTimeout(() => {
                window.location.replace(res.headers.get('Location'));
            }, 250);
        })
        .catch(err => {
            displayMessage(err instanceof DOMException ? err.message : 'Invalid user');
        });
    return false;
};
