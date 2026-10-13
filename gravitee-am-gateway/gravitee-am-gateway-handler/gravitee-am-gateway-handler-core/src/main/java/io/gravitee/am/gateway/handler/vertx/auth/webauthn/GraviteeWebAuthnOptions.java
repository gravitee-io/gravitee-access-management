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

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry;
import io.vertx.ext.auth.impl.jose.JWS;
import io.vertx.ext.auth.webauthn.WebAuthnOptions;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_KEY;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_SAFETYNET;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.APPLE;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.MDS;

/**
 * WebAuthn options whose root certificates come from the gateway trust anchors only, the ones embedded in
 * vertx-auth-webauthn being dropped.
 * <p>
 * vertx-auth-webauthn holds a single root per attestation format: the first trust anchor of each format is
 * used. Android Key accepts several roots: every Android Key trust anchor is also registered as an
 * additional root, checked by {@code GraviteeAndroidKeyAttestation}.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class GraviteeWebAuthnOptions extends WebAuthnOptions {

    private final Map<String, List<X509Certificate>> additionalRootCertificates = new HashMap<>();

    public GraviteeWebAuthnOptions(WebAuthnTrustAnchorRegistry trustAnchors) {
        super();
        setRootCertificates(null);
        for (String format : List.of(ANDROID_KEY, ANDROID_SAFETYNET, APPLE, MDS)) {
            List<X509Certificate> roots = trustAnchors.roots(format);
            if (roots.isEmpty()) {
                continue;
            }
            putRootCertificate(format, encode(roots.get(0)));
            if (roots.size() > 1 && !ANDROID_KEY.equals(format)) {
                log.warn("Only one WebAuthn trust anchor is supported for {}: {} is used, the {} others are ignored",
                        format, roots.get(0).getSubjectX500Principal(), roots.size() - 1);
            }
        }
        trustAnchors.roots(ANDROID_KEY).forEach(root -> pushAdditionalRootCertificate(ANDROID_KEY, root));
    }

    @Override
    public WebAuthnOptions putRootCertificate(String key, String value) {
        try {
            return super.putRootCertificate(key, value);
        } catch (IllegalArgumentException e) {
            if(log.isDebugEnabled()){
                log.warn("Root Certificate {} can't be loaded due to {}, please update the certificate.", key, e.getMessage(), e);
            } else {
                log.warn("Root Certificate {} can't be loaded due to {}, please update the certificate.", key, e.getMessage());
            }
            return this;
        }
    }

    public List<X509Certificate> getAdditionalRootCertificate(String key) {
        return this.additionalRootCertificates.get(key);
    }

    public WebAuthnOptions pushAdditionalRootCertificate(String key, String value) {
        try {
            X509Certificate cert = JWS.parseX5c(value);
            cert.checkValidity();
            pushAdditionalRootCertificate(key, cert);
        } catch (CertificateException e) {
            if(log.isDebugEnabled()){
                log.warn("Invalid additional root certificate for {}", key, e);
            } else {
                log.warn("Invalid additional root certificate for {}", key);
            }
        }
        return this;
    }

    public WebAuthnOptions pushAdditionalRootCertificate(String key, X509Certificate certificate) {
        this.additionalRootCertificates.computeIfAbsent(key, k -> new ArrayList<>()).add(certificate);
        return this;
    }

    private static String encode(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
