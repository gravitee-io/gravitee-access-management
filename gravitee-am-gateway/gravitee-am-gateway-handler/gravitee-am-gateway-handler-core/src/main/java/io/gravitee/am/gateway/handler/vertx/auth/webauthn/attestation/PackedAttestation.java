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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.attestation;

import io.gravitee.am.gateway.handler.vertx.auth.jose.JWK;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.AuthenticatorData;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class PackedAttestation implements Attestation {

    // codecs
    private static final Base64.Decoder b64dec = Base64.getUrlDecoder();

    private final MessageDigest sha256;
    private final CertificateFactory x509;
    private final Signature sig;

    public PackedAttestation() {
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
            x509 = CertificateFactory.getInstance("X.509");
            sig = Signature.getInstance("SHA256withECDSA");
        } catch (NoSuchAlgorithmException | CertificateException e) {
            throw new AttestationException(e);
        }
    }

    @Override
    public String fmt() {
        return "packed";
    }

    @Override
    public void verify(JsonObject webAuthnResponse, byte[] clientDataJSON, JsonObject ctapMakeCredResp, AuthenticatorData authDataStruct) throws AttestationException {
        try {
            byte[] clientDataHash = hash(clientDataJSON);

            byte[] signatureBase = Buffer.buffer()
                    .appendBytes(authDataStruct.getRaw())
                    .appendBytes(clientDataHash)
                    .getBytes();

            JsonObject attStmt = ctapMakeCredResp.getJsonObject("attStmt");
            byte[] signature = b64dec.decode(attStmt.getString("sig"));

            boolean signatureValid;

            if (attStmt.containsKey("x5c")) {
                /* ----- Verify FULL attestation ----- */
                JsonArray x5c = attStmt.getJsonArray("x5c");

                final X509Certificate x509Certificate = (X509Certificate) x509.generateCertificate(new ByteArrayInputStream(b64dec.decode(x5c.getString(0))));
                // check the certificate
                x509Certificate.checkValidity();
                // certificate valid lets verify the principal
                String[] values = x509Certificate.getSubjectX500Principal().getName(X500Principal.RFC2253).split(",");
                int count = 0;

                for (String value : values) {
                    if (value.startsWith("OU=")) {
                        if (!value.equals("OU=Authenticator Attestation")) {
                            throw new AttestationException("Batch certificate OU MUST be set strictly to 'Authenticator Attestation'!");
                        }
                        count++;
                        continue;
                    }
                    if (value.startsWith("CN=")) {
                        if (value.equals("CN=")) {
                            throw new AttestationException("Batch certificate CN MUST no be empty!");
                        }
                        count++;
                        continue;
                    }
                    if (value.startsWith("O=")) {
                        if (value.equals("O=")) {
                            throw new AttestationException("Batch certificate O MUST no be empty!");
                        }
                        count++;
                        continue;
                    }
                    if (value.startsWith("C=")) {
                        if (value.length() != 4) {
                            throw new AttestationException("Batch certificate C MUST be set to two character ISO 3166 code!");
                        }
                        count++;
                        continue;
                    }
                }

                if (count != 4) {
                    throw new AttestationException("Batch certificate does not contain the required subject info!");
                }


                if (x509Certificate.getBasicConstraints() != -1) {
                    throw new AttestationException("Batch certificate basic constraints CA MUST be false!");
                }

                if (x509Certificate.getVersion() != 3) {
                    throw new AttestationException("Batch certificate version MUST be 3(ASN1 2)!");
                }

                signatureValid = verifySignature(signature, signatureBase, x509Certificate);
                /* ----- Verify FULL attestation ENDS ----- */
            } else if (attStmt.containsKey("ecdaaKeyId")) {
                throw new AttestationException("ECDAA IS NOT SUPPORTED YET!");
            } else {
                /* ----- Verify SURROGATE attestation ----- */
                JWK key = authDataStruct.getCredentialJWK();
                signatureValid = key.verify(signature, signatureBase);
                /* ----- Verify SURROGATE attestation ENDS ----- */
            }

            if (!signatureValid) {
                throw new AttestationException("Failed to verify the signature!");
            }
        } catch (CertificateException | InvalidKeyException | SignatureException e) {
            throw new AttestationException(e);
        }
    }

    /**
     * Returns SHA-256 digest of the given data.
     *
     * @param data - data to hash
     * @return the hash
     */
    private byte[] hash(byte[] data) {
        synchronized (sha256) {
            sha256.update(data);
            return sha256.digest();
        }
    }

    private boolean verifySignature(byte[] signature, byte[] data, X509Certificate certificate) throws InvalidKeyException, SignatureException {
        synchronized (sig) {
            sig.initVerify(certificate);
            sig.update(data);
            return sig.verify(signature);
        }
    }
}
