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

import io.gravitee.am.common.webauthn.AttestationConveyancePreference;
import io.gravitee.am.common.webauthn.AuthenticatorAttachment;
import io.gravitee.am.common.webauthn.UserVerification;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.vertx.ext.auth.webauthn.RelyingParty;
import io.vertx.reactivex.core.Vertx;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URL;

import static io.vertx.ext.auth.webauthn.Attestation.NONE;
import static io.vertx.ext.auth.webauthn.UserVerification.DISCOURAGED;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnFactory implements FactoryBean<WebAuthn> {

    private static final String DEFAULT_RELYING_PARTY_NAME = "Gravitee.io Access Management";
    private static final String DEFAULT_ORIGIN = "http://localhost:8092";

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
                        .setRelyingParty(getRelyingParty())
                        .setAuthenticatorAttachment(getAuthenticatorAttachment(webAuthnSettings.getAuthenticatorAttachment()))
                        .setUserVerification(getUserVerification(webAuthnSettings.getUserVerification()))
                        .setRequireResidentKey(webAuthnSettings.isRequireResidentKey())
                        .setAttestation(getAttestation(webAuthnSettings.getAttestationConveyancePreference())))
                .authenticatorFetcher(credentialStore::fetch)
                .authenticatorUpdater(credentialStore::store);
    }

    @Override
    public Class<?> getObjectType() {
        return WebAuthn.class;
    }

    public RelyingParty getRelyingParty() {
        RelyingParty relyingParty = new RelyingParty();
        WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        if (webAuthnSettings == null) {
            relyingParty
                    .setName(DEFAULT_RELYING_PARTY_NAME)
                    .setId(RequestUtils.getDomain(DEFAULT_ORIGIN));
        } else {
            relyingParty
                    .setName(webAuthnSettings.getRelyingPartyName() != null ? webAuthnSettings.getRelyingPartyName() : DEFAULT_RELYING_PARTY_NAME)
                    .setId(webAuthnSettings.getRelyingPartyId() != null ? webAuthnSettings.getRelyingPartyId() :
                            (webAuthnSettings.getOrigin() != null ? RequestUtils.getDomain(webAuthnSettings.getOrigin()) : RequestUtils.getDomain(DEFAULT_ORIGIN)));
        }
        return relyingParty;
    }

    private static io.vertx.ext.auth.webauthn.AuthenticatorAttachment getAuthenticatorAttachment(AuthenticatorAttachment authenticatorAttachment) {
        return authenticatorAttachment != null ? io.vertx.ext.auth.webauthn.AuthenticatorAttachment.valueOf(authenticatorAttachment.name()) : null;
    }

    private static io.vertx.ext.auth.webauthn.UserVerification getUserVerification(UserVerification userVerification) {
        return userVerification != null ? io.vertx.ext.auth.webauthn.UserVerification.valueOf(userVerification.name()) : DISCOURAGED;
    }

    private static io.vertx.ext.auth.webauthn.Attestation getAttestation(AttestationConveyancePreference attestationConveyancePreference) {
        return attestationConveyancePreference != null ? io.vertx.ext.auth.webauthn.Attestation.valueOf(attestationConveyancePreference.name()) : NONE;
    }

    private WebAuthn defaultWebAuthn() {
        return WebAuthn.create(
                vertx.getDelegate(), new WebAuthnOptions().setRelyingParty(getRelyingParty()))
                .authenticatorFetcher(credentialStore::fetch)
                .authenticatorUpdater(credentialStore::store);
    }
}
