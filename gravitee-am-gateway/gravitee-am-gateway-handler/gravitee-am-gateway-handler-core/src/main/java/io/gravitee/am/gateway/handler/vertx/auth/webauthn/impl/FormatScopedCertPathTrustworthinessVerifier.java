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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl;

import com.webauthn4j.async.verifier.attestation.trustworthiness.certpath.CertPathTrustworthinessAsyncVerifier;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement;
import com.webauthn4j.verifier.exception.CertificateException;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.AttestationCertificateChains;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Decides whether an attestation certificate chain is trusted, per attestation format, with the rules
 * vertx-auth-webauthn applied in AM:
 * <ul>
 *     <li>{@code android-key}, {@code apple} and {@code android-safetynet} are anchored: the chain must
 *     validate (PKIX) against the trusted roots of the format, with or without its root;</li>
 *     <li>the other formats are not anchored: see {@link AttestationCertificateChains#verifyChain}, the
 *     chain of {@code tpm} not carrying its root.</li>
 * </ul>
 * Unlike webauthn4j's own verifier, roots are never shared between formats.
 *
 * @author GraviteeSource Team
 */
public class FormatScopedCertPathTrustworthinessVerifier implements CertPathTrustworthinessAsyncVerifier {

    private static final String TPM = "tpm";

    private final Map<String, List<X509Certificate>> rootsByFormat;

    /**
     * @param rootsByFormat the trusted roots of each anchored format
     */
    public FormatScopedCertPathTrustworthinessVerifier(Map<String, List<X509Certificate>> rootsByFormat) {
        this.rootsByFormat = Map.copyOf(rootsByFormat);
    }

    @Override
    public CompletionStage<Void> verify(AAGUID aaguid, CertificateBaseAttestationStatement attestationStatement, Instant timestamp) {
        try {
            String format = attestationStatement.getFormat();
            List<X509Certificate> chain = attestationStatement.getX5c();
            if (WebAuthnTrustAnchorRegistry.ANCHORED_FORMATS.contains(format)) {
                AttestationCertificateChains.verifyAgainstRoots(chain, rootsByFormat.getOrDefault(format, List.of()));
            } else {
                AttestationCertificateChains.verifyChain(chain, !TPM.equals(format));
            }
            return CompletableFuture.completedFuture(null);
        } catch (GeneralSecurityException e) {
            return CompletableFuture.failedFuture(new CertificateException("Attestation certificate chain is not trusted: " + e.getMessage(), e));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
