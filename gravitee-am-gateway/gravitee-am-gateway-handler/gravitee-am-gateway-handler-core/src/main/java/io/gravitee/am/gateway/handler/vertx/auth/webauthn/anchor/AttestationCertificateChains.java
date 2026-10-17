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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation of WebAuthn attestation certificate chains (x5c, leaf first).
 *
 * @author GraviteeSource Team
 */
public final class AttestationCertificateChains {

    private AttestationCertificateChains() {
    }

    /**
     * The check applied to the formats that are not anchored on a trusted root: the one of
     * vertx-auth-webauthn without FIDO metadata, kept as is. Every certificate must be valid now; a single
     * certificate needs nothing more; in a longer chain each certificate must be issued and signed by the
     * next one and, when {@code endsWithRoot}, the last one must be self-signed.
     *
     * @param endsWithRoot false for the formats whose chain does not carry its root ({@code tpm},
     *                     {@code android-safetynet})
     */
    public static void verifyChain(List<X509Certificate> chain, boolean endsWithRoot) throws GeneralSecurityException {
        if (chain == null || chain.isEmpty()) {
            throw new CertificateException("empty chain");
        }
        for (int i = 0; i < chain.size(); i++) {
            X509Certificate certificate = chain.get(i);
            certificate.checkValidity();
            if (chain.size() == 1) {
                return;
            }
            if (i + 1 < chain.size()) {
                X509Certificate issuer = chain.get(i + 1);
                if (!certificate.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
                    throw new CertificateException("Certificate path issuers dont match: [" + certificate.getIssuerX500Principal()
                            + "] != [" + issuer.getSubjectX500Principal() + "]");
                }
                certificate.verify(issuer.getPublicKey());
            }
        }
        if (endsWithRoot) {
            X509Certificate root = chain.get(chain.size() - 1);
            root.verify(root.getPublicKey());
        }
    }

    /**
     * PKIX validation of the chain against trusted roots, without revocation checking (attestation roots
     * are validated offline). The chain may end with its root or not: a trailing self-signed certificate is
     * removed from the path, and a chain made of a single self-signed certificate is accepted only if it is
     * one of the roots.
     */
    public static void verifyAgainstRoots(List<X509Certificate> chain, Collection<X509Certificate> roots) throws GeneralSecurityException {
        if (chain == null || chain.isEmpty()) {
            throw new CertificateException("empty chain");
        }
        if (roots.isEmpty()) {
            throw new CertificateException("No trusted root configured");
        }
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        roots.forEach(root -> trustAnchors.add(new TrustAnchor(root, null)));

        List<X509Certificate> path = new ArrayList<>(chain);
        X509Certificate last = path.get(path.size() - 1);
        if (last.getIssuerX500Principal().equals(last.getSubjectX500Principal())) {
            path.remove(path.size() - 1);
        }
        if (path.isEmpty()) {
            if (roots.contains(last)) {
                return;
            }
            throw new CertificateException("Root certificate is invalid: self-signed leaf is not a trusted root");
        }

        CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(path);
        PKIXParameters parameters = pkixParameters(trustAnchors);
        CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
    }

    private static PKIXParameters pkixParameters(Set<TrustAnchor> trustAnchors) throws InvalidAlgorithmParameterException {
        PKIXParameters parameters = new PKIXParameters(trustAnchors);
        parameters.setRevocationEnabled(false);
        return parameters;
    }
}
