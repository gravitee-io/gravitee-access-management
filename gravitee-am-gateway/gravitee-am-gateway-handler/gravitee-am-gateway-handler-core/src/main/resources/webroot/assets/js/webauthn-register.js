'use strict';

const registerForm = document.getElementById('register');
const errorElement = document.getElementById('webauthn-error');

const w = new WebAuthn({
    registerPath: registerForm.action,
    callbackPath: registerForm.action.replace('/webauthn/register', '/webauthn/response')
});

const displayMessage = message => {
    errorElement.getElementsByClassName('error_description')[0].textContent = message;
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
