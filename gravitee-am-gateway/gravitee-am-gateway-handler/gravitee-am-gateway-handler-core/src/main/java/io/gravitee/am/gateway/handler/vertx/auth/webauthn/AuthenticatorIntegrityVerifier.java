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

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.AttestationCertificateChains;
import io.gravitee.am.model.Credential;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Checks that the attestation certificates stored with a WebAuthn credential are still valid, for the
 * {@code enforceAuthenticatorIntegrity} option of the domain: every certificate must be valid now and, in a
 * chain, signed by the next one; the last one must be self-signed except for the formats whose chain does
 * not carry its root. This is the check vertx-auth-webauthn ran without FIDO metadata.
 *
 * @author GraviteeSource Team
 */
public class AuthenticatorIntegrityVerifier {

    private static final Set<String> FORMATS_WITHOUT_ROOT = Set.of("none", "android-safetynet", "tpm");

    /**
     * @return true when the stored attestation statement carries certificates to check
     */
    public boolean hasCertificates(Credential credential) {
        JsonArray x5c = statement(credential).getJsonArray("x5c");
        return x5c != null && !x5c.isEmpty();
    }

    /**
     * @throws GeneralSecurityException when a certificate is expired, not yet valid, unreadable, or the
     *                                  chain is broken
     */
    public void verify(Credential credential) throws GeneralSecurityException {
        JsonArray x5c = statement(credential).getJsonArray("x5c");
        if (x5c == null || x5c.isEmpty()) {
            throw new CertificateException("No attestation certificate stored for credential " + credential.getCredentialId());
        }
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> chain = new ArrayList<>(x5c.size());
        for (Object certificate : x5c) {
            chain.add((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(decode(String.valueOf(certificate)))));
        }
        AttestationCertificateChains.verifyChain(chain, !FORMATS_WITHOUT_ROOT.contains(credential.getAttestationStatementFormat()));
    }

    private static JsonObject statement(Credential credential) {
        String statement = credential.getAttestationStatement();
        return statement == null || statement.isBlank() ? new JsonObject() : new JsonObject(statement);
    }

    /**
     * The certificates are stored base64url encoded; standard base64 is accepted too.
     */
    private static byte[] decode(String certificate) throws CertificateException {
        try {
            return certificate.contains("+") || certificate.contains("/")
                    ? Base64.getMimeDecoder().decode(certificate)
                    : Base64.getUrlDecoder().decode(certificate.replace("=", ""));
        } catch (IllegalArgumentException e) {
            throw new CertificateException("Invalid attestation certificate encoding", e);
        }
    }
}
