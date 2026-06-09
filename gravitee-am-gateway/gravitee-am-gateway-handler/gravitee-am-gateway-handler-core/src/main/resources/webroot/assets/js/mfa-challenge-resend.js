(function () {
    const config = window.mfaChallengeResendConfig;
    if (!config || !config.url) {
        return;
    }

    const resendButton = document.getElementById('mfa-resend-button');
    const messageElement = document.getElementById('mfa-resend-message');
    if (!resendButton) {
        return;
    }
    const cooldownSeconds = 5;
    const resendButtonText = resendButton.textContent;
    const sendingText = config.messages.sending;
    let cooldownInterval = null;

    const clearCooldown = () => {
        if (cooldownInterval !== null) {
            clearInterval(cooldownInterval);
            cooldownInterval = null;
        }
    };

    const disableButton = (text) => {
        resendButton.disabled = true;
        resendButton.classList.add('button-disabled');
        resendButton.textContent = text;
    };

    const enableButton = () => {
        clearCooldown();
        resendButton.disabled = false;
        resendButton.classList.remove('button-disabled');
        resendButton.textContent = resendButtonText;
    };

    const startCooldown = () => {
        let remainingSeconds = cooldownSeconds;
        disableButton(resendButtonText + ' (' + remainingSeconds + ')');
        cooldownInterval = setInterval(() => {
            remainingSeconds -= 1;
            if (remainingSeconds <= 0) {
                enableButton();
                return;
            }
            disableButton(resendButtonText + ' (' + remainingSeconds + ')');
        }, 1000);
    };

    const showMessage = (text, isError) => {
        if (!messageElement) {
            return;
        }
        messageElement.textContent = text;
        messageElement.classList.remove('error-text', 'resend-success');
        messageElement.classList.add(isError ? 'error-text' : 'resend-success');
        messageElement.style.visibility = 'visible';
    };

    const hideMessage = () => {
        if (messageElement) {
            messageElement.style.visibility = 'hidden';
        }
    };

    resendButton.addEventListener('click', function () {
        if (resendButton.disabled) {
            return;
        }

        hideMessage();
        clearCooldown();
        disableButton(sendingText);

        const formData = new FormData();
        if (config.csrfParameterName && config.csrfToken) {
            formData.append(config.csrfParameterName, config.csrfToken);
        }

        fetch(config.url, {
            method: 'POST',
            body: formData,
            credentials: 'same-origin',
            headers: {
                Accept: 'application/json',
            },
        })
            .then(function (response) {
                return response.json().then(function (body) {
                    return { ok: response.ok, status: response.status, body: body };
                });
            })
            .then(function (result) {
                if (result.ok && result.body && result.body.success) {
                    showMessage(config.messages.success, false);
                    return;
                }
                if (result.body && result.body.request_limit_error) {
                    showMessage(config.messages.rateLimit, true);
                    return;
                }
                const description = result.body && result.body.error_description;
                showMessage(description || config.messages.error, true);
            })
            .catch(function () {
                showMessage(config.messages.error, true);
            })
            .finally(function () {
                startCooldown();
            });
    });

    startCooldown();
})();
