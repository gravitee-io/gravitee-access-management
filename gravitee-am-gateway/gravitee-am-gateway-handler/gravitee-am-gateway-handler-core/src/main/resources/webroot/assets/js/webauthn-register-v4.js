'use strict';

const registerForm = document.getElementById('register');
const registerButton = registerForm.querySelector('button, button#register-button');
const errorElement = document.getElementById('webauthn-error');
const credentialsUrl = registerForm.action.replace('/webauthn/register', '/webauthn/register/credentials');

const displayMessage = message => {
    errorElement.getElementsByClassName('error_description')[0].innerHTML = message;
    errorElement.style.display = 'block';
};

const clearMessage = () => {
    errorElement.style.display = 'none';
};

function register(data) {
    var xhr = new XMLHttpRequest();
    xhr.open('POST', credentialsUrl, true);
    xhr.setRequestHeader('Content-Type', 'application/json; charset=utf-8');
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4) {
            if (xhr.status === 200) {
                var makeCredentialOptions = JSON.parse(xhr.responseText);
                makeCredentialOptions.challenge = base64ToBuffer(makeCredentialOptions.challenge);
                makeCredentialOptions.user.id = base64ToBuffer(makeCredentialOptions.user.id);
                if (makeCredentialOptions.excludeCredentials) {
                    for (var i = 0; i < makeCredentialOptions.excludeCredentials.length; i++) {
                        makeCredentialOptions.excludeCredentials[i].id = base64ToBuffer(makeCredentialOptions.excludeCredentials[i].id);
                    }
                }
                navigator.credentials.create({ publicKey: makeCredentialOptions })
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
                        setTimeout(function () {
                            var input = document.createElement('input');
                            input.setAttribute('type', 'hidden');
                            input.setAttribute('name', 'assertion');
                            input.setAttribute('value', res);
                            registerForm.appendChild(input);
                            registerForm.submit();
                        }, 500);
                    })
                    .catch(function (err) {
                        var rpId = null;
                        try {
                            if (makeCredentialOptions && makeCredentialOptions.rp && makeCredentialOptions.rp.id) {
                                rpId = makeCredentialOptions.rp.id;
                            }
                        } catch (e) { /* ignore */ }
                        if (typeof reportWebauthnClientError === 'function') {
                            var regUsername = '';
                            try {
                                var ru = registerForm.username;
                                regUsername = ru && ru.value ? ru.value : '';
                            } catch (e1) { /* ignore */ }
                            reportWebauthnClientError(
                                    resolveWebauthnErrorUrlFromForm(registerForm),
                                    'register',
                                    'create',
                                    err,
                                    rpId,
                                    regUsername,
                                    registerForm);
                        }
                        displayMessage(err instanceof DOMException ? err.message : 'Invalid user');
                    });
            } else {
                displayMessage('Invalid request');
            }
        }
    };
    xhr.send(JSON.stringify(data || {}));
}

registerButton.addEventListener('click', function (event) {
    event.preventDefault();
    register({
        name: registerForm.username.value,
        displayName: registerForm.username.value
    });
});
