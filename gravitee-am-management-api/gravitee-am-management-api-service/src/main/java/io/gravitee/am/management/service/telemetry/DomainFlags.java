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
package io.gravitee.am.management.service.telemetry;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a {@link Domain} to the feature flags that both reports carry. The summary counts the
 * domains with each flag on; a domain record carries the flags themselves. One mapping serves both,
 * so the two reports can never disagree.
 *
 * @author GraviteeSource Team
 */
public final class DomainFlags {

    private DomainFlags() {}

    public static Map<String, Boolean> of(Domain domain) {
        final Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("dynamicClientRegistration", dynamicClientRegistration(domain));
        flags.put("uma", domain.getUma() != null && domain.getUma().isEnabled());
        flags.put("scim", domain.getScim() != null && domain.getScim().isEnabled());
        flags.put("saml", domain.getSaml() != null && domain.getSaml().isEnabled());
        flags.put("webAuthn", domain.getWebAuthnSettings() != null);
        flags.put(
            "selfServiceAccountManagement",
            domain.getSelfServiceAccountManagementSettings() != null && domain.getSelfServiceAccountManagementSettings().isEnabled()
        );
        final LoginSettings login = domain.getLoginSettings();
        flags.put("loginPasswordless", login != null && login.isPasswordlessEnabled());
        flags.put("loginIdentifierFirst", login != null && login.isIdentifierFirstEnabled());
        flags.put("loginRegister", login != null && login.isRegisterEnabled());
        flags.put("loginForgotPassword", login != null && login.isForgotPasswordEnabled());
        flags.put("loginRememberMe", login != null && login.isRememberMeEnabled());
        flags.put("passwordPolicy", domain.getPasswordSettings() != null);
        return flags;
    }

    private static boolean dynamicClientRegistration(Domain domain) {
        final OIDCSettings oidc = domain.getOidc();
        if (oidc == null) {
            return false;
        }
        final ClientRegistrationSettings registration = oidc.getClientRegistrationSettings();
        return registration != null && registration.isDynamicClientRegistrationEnabled();
    }
}
