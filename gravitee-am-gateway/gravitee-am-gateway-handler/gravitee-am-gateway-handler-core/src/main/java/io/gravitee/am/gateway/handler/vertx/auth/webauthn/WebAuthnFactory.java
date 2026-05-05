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
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.ext.auth.webauthn4j.AuthenticatorTransport;
import io.vertx.ext.auth.webauthn4j.RelyingParty;
import io.vertx.ext.auth.webauthn4j.WebAuthn4J;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JOptions;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.vertx.ext.auth.webauthn4j.Attestation.NONE;
import static io.vertx.ext.auth.webauthn4j.UserVerification.DISCOURAGED;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnFactory implements FactoryBean<WebAuthn4J> {

    private static final String DEFAULT_RELYING_PARTY_NAME = "Gravitee.io Access Management";
    private static final String DEFAULT_ORIGIN = "http://localhost:8092";

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private RepositoryCredentialStore credentialStore;

    @Override
    public WebAuthn4J getObject() {
        WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        if (webAuthnSettings == null) {
            return defaultWebAuthn();
        }

        // create WebAuthn options
        WebAuthn4JOptions webAuthnOptions = new GraviteeWebAuthnOptions()
                .setRelyingParty(getRelyingParty())
                .setAuthenticatorAttachment(getAuthenticatorAttachment(webAuthnSettings.getAuthenticatorAttachment()))
                .setUserVerification(getUserVerification(webAuthnSettings.getUserVerification()))
                .setRequireResidentKey(webAuthnSettings.isRequireResidentKey())
                .setAttestation(getAttestation(webAuthnSettings.getAttestationConveyancePreference()))
                .setTransports(getTransports(webAuthnSettings.getAuthenticatorAttachment()))
                .setUseMetadata(webAuthnSettings.isEnforceAuthenticatorIntegrity());

        // register custom certificates if any
        if (webAuthnSettings.getCertificates() != null && !webAuthnSettings.getCertificates().isEmpty()) {
            webAuthnSettings.getCertificates()
                    .forEach((k, v) -> webAuthnOptions.putRootCertificate(k, (String) v));
        }

        return WebAuthn4J.create(vertx.getDelegate(), webAuthnOptions)
                .credentialStorage(credentialStore);
    }

    @Override
    public Class<?> getObjectType() {
        return WebAuthn4J.class;
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

    private static io.vertx.ext.auth.webauthn4j.AuthenticatorAttachment getAuthenticatorAttachment(AuthenticatorAttachment authenticatorAttachment) {
        return authenticatorAttachment != null ? io.vertx.ext.auth.webauthn4j.AuthenticatorAttachment.valueOf(authenticatorAttachment.name()) : null;
    }

    private static io.vertx.ext.auth.webauthn4j.UserVerification getUserVerification(UserVerification userVerification) {
        return userVerification != null ? io.vertx.ext.auth.webauthn4j.UserVerification.valueOf(userVerification.name()) : DISCOURAGED;
    }

    private static io.vertx.ext.auth.webauthn4j.Attestation getAttestation(AttestationConveyancePreference attestationConveyancePreference) {
        return attestationConveyancePreference != null ? io.vertx.ext.auth.webauthn4j.Attestation.valueOf(attestationConveyancePreference.name()) : NONE;
    }

    private static List<AuthenticatorTransport> getTransports(AuthenticatorAttachment authenticatorAttachment) {
        if (authenticatorAttachment == null) {
            return Arrays.asList(AuthenticatorTransport.values());
        }

        switch (authenticatorAttachment) {
            case PLATFORM:
                return Collections.singletonList(AuthenticatorTransport.INTERNAL);
            case CROSS_PLATFORM:
                return Arrays.asList(AuthenticatorTransport.USB, AuthenticatorTransport.BLE, AuthenticatorTransport.NFC);
            default:
                throw new IllegalArgumentException("Unknown authenticator attachment : " + authenticatorAttachment);
        }
    }

    private WebAuthn4J defaultWebAuthn() {
        return WebAuthn4J.create(
                vertx.getDelegate(), new GraviteeWebAuthnOptions().setRelyingParty(getRelyingParty()))
                .credentialStorage(credentialStore);
    }
}
