'use strict';

var submitBtn = document.getElementById('verify');
var loginPath = document.getElementById('webAuthnLoginPath').getAttribute('value');
var webAuthnErrorPathEl = document.getElementById('webAuthnErrorPath');
var webAuthnErrorPath = webAuthnErrorPathEl ? webAuthnErrorPathEl.getAttribute('value') : null;
var userName = document.getElementById('userName').getAttribute('value');
var loginForm = document.getElementById('form');

submitBtn.addEventListener('click', function (e) {
    e.preventDefault();
    var publicKeyOptions = null;

    var xhr = new XMLHttpRequest();
    xhr.open('POST', loginPath, true);
    xhr.setRequestHeader('Content-Type', 'application/json; charset=utf-8');
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.onreadystatechange = function () {
        if (xhr.readyState !== 4) {
            return;
        }
        if (xhr.status !== 200) {
            console.error(new Error(xhr.statusText || 'request failed'));
            return;
        }
        try {
            var res = JSON.parse(xhr.responseText);
            res.challenge = base64ToBuffer(res.challenge);
            if (res.allowCredentials) {
                for (var i = 0; i < res.allowCredentials.length; i++) {
                    res.allowCredentials[i].id = base64ToBuffer(res.allowCredentials[i].id);
                }
            }
            publicKeyOptions = res;
            navigator.credentials.get({ publicKey: res })
                .then(function (cred) {
                    var credential = JSON.stringify({
                        id: cred.id,
                        rawId: bufferToBase64(cred.rawId),
                        response: {
                            clientDataJSON: bufferToBase64(cred.response.clientDataJSON),
                            authenticatorData: bufferToBase64(cred.response.authenticatorData),
                            signature: bufferToBase64(cred.response.signature),
                            userHandle: bufferToBase64(cred.response.userHandle),
                        },
                        type: cred.type
                    });
                    var input = document.createElement('input');
                    input.setAttribute('type', 'hidden');
                    input.setAttribute('name', 'code');
                    input.setAttribute('value', credential);
                    loginForm.appendChild(input);
                    loginForm.submit();
                })
                .catch(function (err) {
                    var rpId = publicKeyOptions && publicKeyOptions.rpId ? publicKeyOptions.rpId : null;
                    if (typeof reportWebauthnClientError === 'function' && webAuthnErrorPath) {
                        reportWebauthnClientError(
                                webAuthnErrorPath,
                                'mfa',
                                'get',
                                err,
                                rpId,
                                userName,
                                loginForm);
                    }
                    console.error(err);
                });
        } catch (parseErr) {
            console.error(parseErr);
        }
    };
    xhr.send(JSON.stringify({ name: userName }));
    return false;
});
