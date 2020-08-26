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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import io.gravitee.am.common.webauthn.AuthenticatorAttachment;
import io.gravitee.am.common.webauthn.UserVerification;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.vertx.ext.auth.webauthn.CredentialStore;
import io.vertx.ext.auth.webauthn.RelayParty;
import io.vertx.ext.auth.webauthn.WebAuthnOptions;
import io.vertx.reactivex.core.Vertx;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnFactory implements FactoryBean<WebAuthn> {

    private static final String DEFAULT_ORIGIN = "http://localhost:8092";
    private static final String DEFAULT_RELYING_PARTY_NAME = "Gravitee.io Access Management";

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private CredentialStore credentialStore;

    @Override
    public WebAuthn getObject() throws Exception {
        WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        if (webAuthnSettings == null) {
            return defaultWebAuthn();
        }

        return WebAuthn.create(
                vertx.getDelegate(),
                new WebAuthnOptions()
                        .setOrigin(webAuthnSettings.getOrigin() != null ? webAuthnSettings.getOrigin() : DEFAULT_ORIGIN)
                        .setRelayParty(
                                new RelayParty()
                                        .setName(webAuthnSettings.getRelyingPartyName() != null ? webAuthnSettings.getRelyingPartyName() : DEFAULT_RELYING_PARTY_NAME))
                .setAuthenticatorAttachment(getAuthenticatorAttachment(webAuthnSettings.getAuthenticatorAttachment()))
                .setRequireResidentKey(webAuthnSettings.isRequireResidentKey())
                .setUserVerification(getUserVerification(webAuthnSettings.getUserVerification()))
                , credentialStore);
    }

    @Override
    public Class<?> getObjectType() {
        return WebAuthn.class;
    }

    private static io.vertx.ext.auth.webauthn.AuthenticatorAttachment getAuthenticatorAttachment(AuthenticatorAttachment authenticatorAttachment) {
        return authenticatorAttachment != null ? io.vertx.ext.auth.webauthn.AuthenticatorAttachment.valueOf(authenticatorAttachment.name()) : null;
    }

    private static io.vertx.ext.auth.webauthn.UserVerification getUserVerification(UserVerification userVerification) {
        return userVerification != null ? io.vertx.ext.auth.webauthn.UserVerification.valueOf(userVerification.name()) : null;
    }

    private WebAuthn defaultWebAuthn() {
        return WebAuthn.create(
                vertx.getDelegate(),
                new WebAuthnOptions()
                        .setOrigin(DEFAULT_ORIGIN)
                        .setRelayParty(new RelayParty().setName(DEFAULT_RELYING_PARTY_NAME)), credentialStore);
    }
}
