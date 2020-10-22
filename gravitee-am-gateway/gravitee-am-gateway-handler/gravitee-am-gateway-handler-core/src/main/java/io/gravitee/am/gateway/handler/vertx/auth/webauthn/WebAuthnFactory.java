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
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.vertx.ext.auth.webauthn.RelyingParty;
import io.vertx.reactivex.core.Vertx;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnFactory implements FactoryBean<WebAuthn> {

    private static final String DEFAULT_RELYING_PARTY_NAME = "Gravitee.io Access Management";

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private RepositoryCredentialStore credentialStore;

    @Override
    public WebAuthn getObject() {
        WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        if (webAuthnSettings == null) {
            return defaultWebAuthn();
        }

        return WebAuthn.create(
                vertx.getDelegate(),
                new WebAuthnOptions()
                        .setRelyingParty(new RelyingParty().setName(DEFAULT_RELYING_PARTY_NAME))
                        .setAuthenticatorAttachment(getAuthenticatorAttachment(webAuthnSettings.getAuthenticatorAttachment()))
                        .setUserVerification(getUserVerification(webAuthnSettings.getUserVerification()))
                        .setRequireResidentKey(webAuthnSettings.isRequireResidentKey()))
                .authenticatorFetcher(credentialStore::fetch)
                .authenticatorUpdater(credentialStore::store);
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
                new WebAuthnOptions().setRelyingParty(new RelyingParty().setName(DEFAULT_RELYING_PARTY_NAME)))
                .authenticatorFetcher(credentialStore::fetch)
                .authenticatorUpdater(credentialStore::store);
    }
}
