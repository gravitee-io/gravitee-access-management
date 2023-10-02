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
const errorElement = document.getElementById('webauthn-error');

const w = new WebAuthn({
    loginPath: loginForm.action,
    callbackPath:  loginForm.action.replace('/webauthn/login', '/webauthn/response')
});

const displayMessage = message => {
    errorElement.getElementsByClassName('error_description')[0].innerHTML = message;
    errorElement.style.display = 'block';
};

const clearMessage = () => {
    errorElement.style.display = 'none';
};

loginForm.addEventListener("submit", function(e){
    e.preventDefault();
    let user = {
        name: this.username.value
    };
    if(this.deviceId && this.deviceId.value){
        user.deviceId = this.deviceId.value;
    }
    if(this.deviceType && this.deviceType.value){
        user.deviceType = this.deviceType.value;
    }
    if(this.user_consent_ip_location && this.user_consent_ip_location.value){
        user.user_consent_ip_location = this.user_consent_ip_location.value;
    }
    if(this.user_consent_user_agent && this.user_consent_user_agent.value){
        user.user_consent_user_agent = this.user_consent_user_agent.value;
    }

    w.login(user)
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
});
