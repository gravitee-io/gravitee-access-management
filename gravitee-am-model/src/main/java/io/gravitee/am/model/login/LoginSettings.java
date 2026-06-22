/**
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
package io.gravitee.am.model.login;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "Login settings", description = "Configuration of the domain's login flow and the features " +
        "offered on the sign-in page.")
public class LoginSettings {

    @Schema(description = "Whether these login settings are inherited from a parent scope rather than defined " +
            "here. When true, the other fields are ignored.", defaultValue = "true")
    private boolean inherited = true;
    @Schema(description = "Whether users can initiate a forgot-password flow from the login page.",
            defaultValue = "false")
    private boolean forgotPasswordEnabled;
    @Schema(description = "Whether users can self-register from the login page.", defaultValue = "false")
    private boolean registerEnabled;
    @Schema(description = "Whether the login page offers a remember-me option.", defaultValue = "false")
    private boolean rememberMeEnabled;
    @Schema(description = "Whether passwordless (WebAuthn) authentication is offered.", defaultValue = "false")
    private boolean passwordlessEnabled;
    @Schema(description = "Whether a passwordless device can be remembered to skip future challenges.",
            defaultValue = "false")
    private boolean passwordlessRememberDeviceEnabled;
    @Schema(description = "Whether a password is still required alongside passwordless authentication.",
            defaultValue = "false")
    private boolean passwordlessEnforcePasswordEnabled;
    @Schema(description = "Period, in seconds, after which the user's credentials must be re-entered to keep " +
            "using passwordless authentication.")
    private Integer passwordlessEnforcePasswordMaxAge;
    @Schema(description = "Whether users can name their passwordless devices.", defaultValue = "false")
    private boolean passwordlessDeviceNamingEnabled;
    @Schema(description = "Period, in seconds, during which WebAuthn registration is not prompted again after " +
            "the user skips enrollment.", defaultValue = "2592000")
    private Long passwordlessRegistrationSkipTimeSeconds;
    @Schema(description = "Whether certificate-based authentication is offered.", defaultValue = "false")
    private boolean certificateBasedAuthEnabled;
    @Schema(description = "URL used for certificate-based authentication.")
    private String certificateBasedAuthUrl;
    @Schema(description = "Whether magic-link authentication is offered.", defaultValue = "false")
    private boolean magicLinkAuthEnabled;
    @Schema(description = "Whether the login form is hidden (for example when only social or identifier-first " +
            "login is offered).", defaultValue = "false")
    private boolean hideForm;
    @Schema(description = "Whether identifier-first login is enabled, prompting for the username before the " +
            "password.", defaultValue = "false")
    private boolean identifierFirstEnabled;

    @Schema(description = "Whether the user is forced to reset their password once it expires.")
    private Boolean resetPasswordOnExpiration;

    public LoginSettings() {
    }

    public LoginSettings(LoginSettings other) {
        this.inherited = other.inherited;
        this.forgotPasswordEnabled = other.forgotPasswordEnabled;
        this.registerEnabled = other.registerEnabled;
        this.rememberMeEnabled = other.rememberMeEnabled;
        this.passwordlessEnabled = other.passwordlessEnabled;
        this.passwordlessRememberDeviceEnabled = other.passwordlessRememberDeviceEnabled;
        this.passwordlessEnforcePasswordEnabled = other.passwordlessEnforcePasswordEnabled;
        this.passwordlessEnforcePasswordMaxAge = other.passwordlessEnforcePasswordMaxAge;
        this.passwordlessDeviceNamingEnabled = other.passwordlessDeviceNamingEnabled;
        this.passwordlessRegistrationSkipTimeSeconds = other.passwordlessRegistrationSkipTimeSeconds;
        this.certificateBasedAuthEnabled = other.certificateBasedAuthEnabled;
        this.certificateBasedAuthUrl = other.certificateBasedAuthUrl;
        this.magicLinkAuthEnabled = other.magicLinkAuthEnabled;
        this.hideForm = !other.identifierFirstEnabled && other.hideForm;
        this.identifierFirstEnabled = other.identifierFirstEnabled;
        this.resetPasswordOnExpiration = other.resetPasswordOnExpiration;
    }

    @Schema(hidden = true)
    public boolean isEnforcePasswordPolicyEnabled() {
        return passwordlessEnforcePasswordEnabled
                && passwordlessEnforcePasswordMaxAge != null;
    }

    public static LoginSettings getInstance(Domain domain, Client client) {
        // if client has no login config return domain config
        if (client == null || client.getLoginSettings() == null) {
            return domain.getLoginSettings();
        }

        // if client configuration is not inherited return the client config
        if (!client.getLoginSettings().isInherited()) {
            return client.getLoginSettings();
        }

        // return domain config
        return domain.getLoginSettings();
    }
}
