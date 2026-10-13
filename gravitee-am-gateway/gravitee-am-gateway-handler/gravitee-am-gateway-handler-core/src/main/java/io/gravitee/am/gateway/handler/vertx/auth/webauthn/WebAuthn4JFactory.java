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
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.GraviteeWebAuthn4J;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStorage;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.ext.auth.webauthn4j.Attestation;
import io.vertx.ext.auth.webauthn4j.AuthenticatorTransport;
import io.vertx.ext.auth.webauthn4j.RelyingParty;
import io.vertx.ext.auth.webauthn4j.WebAuthn4J;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JOptions;
import io.vertx.rxjava3.core.Vertx;
import lombok.CustomLog;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the {@link WebAuthn4J} of the domain from its WebAuthn settings.
 * <p>
 * The trusted roots of each anchored attestation format are the gateway trust anchors plus the
 * certificate the domain declares for this format in its settings.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class WebAuthn4JFactory implements FactoryBean<WebAuthn4J> {

    private static final String DEFAULT_RELYING_PARTY_NAME = "Gravitee.io Access Management";
    private static final String DEFAULT_ORIGIN = "http://localhost:8092";
    private static final Map<String, String> CERTIFICATE_KEY_ALIASES = Map.of("u2f", "fido-u2f");

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private RepositoryCredentialStorage credentialStorage;

    @Autowired
    private WebAuthnTrustAnchorRegistry trustAnchorRegistry;

    @Override
    public WebAuthn4J getObject() {
        WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        WebAuthn4JOptions options = new WebAuthn4JOptions().setRelyingParty(getRelyingParty());
        if (webAuthnSettings != null) {
            options
                    .setAuthenticatorAttachment(getAuthenticatorAttachment(webAuthnSettings.getAuthenticatorAttachment()))
                    .setUserVerification(getUserVerification(webAuthnSettings.getUserVerification()))
                    .setRequireResidentKey(webAuthnSettings.isRequireResidentKey())
                    .setAttestation(getAttestation(webAuthnSettings.getAttestationConveyancePreference()))
                    .setTransports(getTransports(webAuthnSettings.getAuthenticatorAttachment()));
        }
        return new GraviteeWebAuthn4J(vertx.getDelegate(), options, trustedRoots(webAuthnSettings))
                .credentialStorage(credentialStorage);
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

    /**
     * The gateway trust anchors of each anchored format, plus the certificate of the domain settings for
     * this format. A domain certificate that cannot be read is skipped: it must not prevent the domain from
     * starting.
     */
    Map<String, List<X509Certificate>> trustedRoots(WebAuthnSettings webAuthnSettings) {
        Map<String, List<X509Certificate>> roots = new HashMap<>();
        WebAuthnTrustAnchorRegistry.ANCHORED_FORMATS.forEach(format -> roots.put(format, new ArrayList<>(trustAnchorRegistry.roots(format))));
        if (webAuthnSettings == null || webAuthnSettings.getCertificates() == null) {
            return roots;
        }
        webAuthnSettings.getCertificates().forEach((key, value) -> {
            String format = key.toLowerCase(Locale.ROOT);
            format = CERTIFICATE_KEY_ALIASES.getOrDefault(format, format);
            if (!WebAuthnTrustAnchorRegistry.ANCHORED_FORMATS.contains(format)) {
                log.debug("Domain {} WebAuthn certificate {} ignored: {} attestations are not anchored on a root certificate", domain.getId(), key, format);
                return;
            }
            try {
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(Base64.getMimeDecoder().decode(String.valueOf(value))));
                certificate.checkValidity();
                List<X509Certificate> formatRoots = roots.get(format);
                if (!formatRoots.contains(certificate)) {
                    formatRoots.add(certificate);
                }
            } catch (CertificateException | IllegalArgumentException e) {
                log.warn("Root Certificate {} can't be loaded due to {}, please update the certificate.", key, e.getMessage());
            }
        });
        return roots;
    }

    private static io.vertx.ext.auth.webauthn4j.AuthenticatorAttachment getAuthenticatorAttachment(AuthenticatorAttachment authenticatorAttachment) {
        return authenticatorAttachment != null ? io.vertx.ext.auth.webauthn4j.AuthenticatorAttachment.valueOf(authenticatorAttachment.name()) : null;
    }

    private static io.vertx.ext.auth.webauthn4j.UserVerification getUserVerification(UserVerification userVerification) {
        return userVerification != null ? io.vertx.ext.auth.webauthn4j.UserVerification.valueOf(userVerification.name()) : io.vertx.ext.auth.webauthn4j.UserVerification.DISCOURAGED;
    }

    private static Attestation getAttestation(AttestationConveyancePreference attestationConveyancePreference) {
        return attestationConveyancePreference != null ? Attestation.valueOf(attestationConveyancePreference.name()) : Attestation.NONE;
    }

    private static List<AuthenticatorTransport> getTransports(AuthenticatorAttachment authenticatorAttachment) {
        if (authenticatorAttachment == null) {
            return Arrays.asList(AuthenticatorTransport.values());
        }
        return switch (authenticatorAttachment) {
            case PLATFORM -> List.of(AuthenticatorTransport.INTERNAL);
            case CROSS_PLATFORM -> List.of(AuthenticatorTransport.USB, AuthenticatorTransport.BLE, AuthenticatorTransport.NFC);
        };
    }
}
