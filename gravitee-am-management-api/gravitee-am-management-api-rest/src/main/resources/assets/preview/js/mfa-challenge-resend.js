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
        resendButton.disabled = true;

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
                resendButton.disabled = false;
            });
    });
})();
