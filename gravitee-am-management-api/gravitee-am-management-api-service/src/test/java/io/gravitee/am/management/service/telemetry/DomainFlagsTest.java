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

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.SAMLSettings;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * @author GraviteeSource Team
 */
class DomainFlagsTest {

    @Test
    void shouldReportEveryFlagOffForAnEmptyDomain() {
        final Map<String, Boolean> flags = DomainFlags.of(new Domain());
        assertThat(flags).hasSize(12).containsOnlyKeys(
            "dynamicClientRegistration",
            "uma",
            "scim",
            "saml",
            "webAuthn",
            "selfServiceAccountManagement",
            "loginPasswordless",
            "loginIdentifierFirst",
            "loginRegister",
            "loginForgotPassword",
            "loginRememberMe",
            "passwordPolicy"
        );
        assertThat(flags.values()).containsOnly(false);
    }

    @Test
    void shouldReadTheDomainSettings() {
        final Domain domain = new Domain();

        final ClientRegistrationSettings registration = new ClientRegistrationSettings();
        registration.setDynamicClientRegistrationEnabled(true);
        final OIDCSettings oidc = new OIDCSettings();
        oidc.setClientRegistrationSettings(registration);
        domain.setOidc(oidc);

        final UMASettings uma = new UMASettings();
        uma.setEnabled(true);
        domain.setUma(uma);

        final SCIMSettings scim = new SCIMSettings();
        scim.setEnabled(true);
        domain.setScim(scim);

        final SAMLSettings saml = new SAMLSettings();
        saml.setEnabled(true);
        domain.setSaml(saml);

        domain.setWebAuthnSettings(new WebAuthnSettings());

        final SelfServiceAccountManagementSettings selfService = new SelfServiceAccountManagementSettings();
        selfService.setEnabled(true);
        domain.setSelfServiceAccountManagementSettings(selfService);

        final LoginSettings login = new LoginSettings();
        login.setPasswordlessEnabled(true);
        login.setIdentifierFirstEnabled(true);
        login.setRegisterEnabled(true);
        login.setForgotPasswordEnabled(true);
        login.setRememberMeEnabled(true);
        domain.setLoginSettings(login);

        domain.setPasswordSettings(new PasswordSettings());

        assertThat(DomainFlags.of(domain).values()).containsOnly(true);
    }

    @Test
    void shouldReportADisabledFeatureAsOff() {
        final Domain domain = new Domain();
        final SCIMSettings scim = new SCIMSettings();
        scim.setEnabled(false);
        domain.setScim(scim);

        assertThat(DomainFlags.of(domain)).containsEntry("scim", false);
    }
}
