'use strict';

const submitBtn = document.getElementById('verify');
const loginPath = document.getElementById("webAuthnLoginPath").getAttribute("value")
const userName = document.getElementById("userName").getAttribute("value")
const factorId = document.getElementById("factorId").getAttribute("value")
const loginForm = document.getElementById('form');

submitBtn.addEventListener("click", function(e){
    e.preventDefault();
    let user = {
        name: userName
    };

    fetch(loginPath, {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(user)
    })
        .then(res => {
            if (res.status === 200) {
                return res;
            }
            throw new Error(res.statusText);
        })
        .then(res => res.json())
        .then(res => {
            res.challenge = base64ToBuffer(res.challenge);
            if (res.allowCredentials) {
                for (let i = 0; i < res.allowCredentials.length; i++) {
                    res.allowCredentials[i].id = base64ToBuffer(res.allowCredentials[i].id);
                }
            }
            return res;
        })
        .then(res => navigator.credentials.get({publicKey: res}))
        .then(res => {
            const credential = JSON.stringify({
                id: res.id,
                rawId: bufferToBase64(res.rawId),
                response: {
                    clientDataJSON: bufferToBase64(res.response.clientDataJSON),
                    authenticatorData: bufferToBase64(res.response.authenticatorData),
                    signature: bufferToBase64(res.response.signature),
                    userHandle: bufferToBase64(res.response.userHandle),
                },
                type: res.type
            });

            const input = document.createElement("input");
            input.setAttribute("type", "hidden");
            input.setAttribute("name", "code");
            input.setAttribute("value", credential);
            loginForm.appendChild(input);
            loginForm.submit();
        })
       .catch(err => {
            console.error(err)
        });
    return false;
});