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
/*
 * Copyright 2019 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.attestation;

import io.gravitee.am.gateway.handler.vertx.auth.CertificateHelper;
import io.gravitee.am.gateway.handler.vertx.auth.jose.JWK;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.PublicKeyCredential;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthnOptions;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.ASN1;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.AuthData;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.metadata.MetaData;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.metadata.MetaDataException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;

import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.ASN1.OCTET_STRING;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.ASN1.parseASN1;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.attestation.Attestation.*;

/**
 * Implementation of the FIDO "packed" attestation check.
 *
 * @author <a href="mailto:pmlopes@gmail.com>Paulo Lopes</a>
 */
// TODO to remove when updating to vert.x 4
public class PackedAttestation implements Attestation {

    private final Set<String> ISO3166 = new HashSet<>();

    public PackedAttestation() {
        // preload the country codes
        ISO3166.addAll(Arrays.asList(Locale.getISOCountries()));
    }

    @Override
    public String fmt() {
        return "packed";
    }

    @Override
    public void validate(WebAuthnOptions options, MetaData metadata, byte[] clientDataJSON, JsonObject attestation, AuthData authData) throws AttestationException {
        try {
            byte[] clientDataHash = hash("SHA-256", clientDataJSON);

            // Check attStmt and it contains “x5c” then its a FULL attestation.
            JsonObject attStmt = attestation.getJsonObject("attStmt");
            byte[] signature = attStmt.getBinary("sig");

            if (attStmt.containsKey("x5c")) {
                // FULL basically means that it’s an attestation that chains to the manufacturer.
                // It is signed by batch private key, who’s public key is in a batch certificate,
                // that is chained to some attestation root certificate.

                List<X509Certificate> certChain = parseX5c(attStmt.getJsonArray("x5c"));

                if (certChain.size() == 0) {
                    throw new AttestationException("no certificates in x5c field");
                }

                // Then check certificate and verify attestation:
                // 1. Extract leaf cert from “x5c” as attCert
                final X509Certificate x509Certificate = certChain.get(0);
                final CertificateHelper.CertInfo certInfo = CertificateHelper.getCertInfo(certChain.get(0));
                // 2. Check that attCert is of version 3(ASN1 INT 2)
                if (certInfo.version() != 3) {
                    throw new AttestationException("Batch certificate version MUST be 3(ASN1 2)");
                }
                // 3. Check that attCert subject country (C) is set to a valid two character ISO 3166 code
                if (!certInfo.subjectHas("C") || !ISO3166.contains(certInfo.subject("C"))) {
                    throw new AttestationException("Batch certificate C MUST be set to two character ISO 3166 code");
                }
                // 4. Check that attCert subject organisation (O) is not empty
                if (!certInfo.subjectHas("O")) {
                    throw new AttestationException("Batch certificate CN MUST no be empty");
                }
                // 5. Check that attCert subject organisation unit (OU) is set to literal string “Authenticator Attestation”
                if (!"Authenticator Attestation".equals(certInfo.subject("OU"))) {
                    throw new AttestationException("Batch certificate OU MUST be set strictly to 'Authenticator Attestation'");
                }
                // 6. Check that attCert subject common name(CN) is not empty.
                if (!certInfo.subjectHas("CN")) {
                    throw new AttestationException("Batch certificate CN MUST no be empty");
                }
                // 7. Check that attCert basic constraints for CA is set to -1
                if (certInfo.basicConstraintsCA() != -1) {
                    throw new AttestationException("Batch certificate basic constraints CA MUST be -1");
                }
                // 8. If certificate contains id-fido-gen-ce-aaguid(1.3.6.1.4.1.45724.1.1.4) extension,
                // then check that its value set to the AAGUID returned by the authenticator in authData.
                byte[] idFidoGenCeAaguid = x509Certificate.getExtensionValue("1.3.6.1.4.1.45724.1.1.4");
                if (idFidoGenCeAaguid != null) {
                    ASN1.ASN extension = ASN1.parseASN1(idFidoGenCeAaguid);
                    if (extension.tag.type != OCTET_STRING) {
                        throw new AttestationException("1.3.6.1.4.1.45724.1.1.4 Extension is not an ASN.1 OCTECT string!");
                    }
                    // parse the octet as ASN.1 and expect it to se a sequence
                    extension = parseASN1(extension.binary(0));
                    if (extension.tag.type != OCTET_STRING) {
                        throw new AttestationException("1.3.6.1.4.1.45724.1.1.4 Extension is not an ASN.1 OCTECT string!");
                    }
                    // match check
                    if (!MessageDigest.isEqual(extension.binary(0), authData.getAaguid())) {
                        throw new AttestationException("Certificate id-fido-gen-ce-aaguid extension does not match authData");
                    }
                }

                // If available, validate attestation alg and x5c with info in the metadata statement
                JsonObject statement = metadata.verifyMetadata(
                        authData.getAaguidString(),
                        PublicKeyCredential.valueOf(attStmt.getInteger("alg")),
                        certChain);

                if (statement != null) {
                    // The presence of x5c means this is a full attestation. Check to see if attestationTypes
                    // includes packed attestations.
                    if (!statement.getJsonArray("attestationTypes").contains(MetaData.ATTESTATION_BASIC_FULL)) {
                        throw new AttestationException("Metadata does not indicate support for full attestations");
                    }
                }

                // Verify the attestation:
                // 1. Concatenate authData with clientDataHash to create signatureBase
                byte[] signatureBase = Buffer.buffer()
                        .appendBytes(authData.getRaw())
                        .appendBytes(clientDataHash)
                        .getBytes();
                // 2. Verify signature “sig” over the signatureBase with the public key
                // extracted from leaf attCert in “x5c”, using the algorithm “alg”
                verifySignature(
                        PublicKeyCredential.valueOf(attStmt.getInteger("alg")),
                        x509Certificate,
                        signature,
                        signatureBase);

            } else if (attStmt.containsKey("ecdaaKeyId")) {
                // If available, validate attestation alg and x5c with info in the metadata statement
                JsonObject statement = metadata.verifyMetadata(
                        authData.getAaguidString(),
                        PublicKeyCredential.valueOf(attStmt.getInteger("alg")),
                        null);

                if (statement != null) {
                    // The presence of x5c means this is a full attestation. Check to see if attestationTypes
                    // includes packed attestations.
                    if (!statement.getJsonArray("attestationTypes").contains(MetaData.ATTESTATION_ECDAA)) {
                        throw new AttestationException("Metadata does not indicate support for ecdaa attestations");
                    }
                }
                throw new AttestationException("ECDAA IS NOT SUPPORTED YET!");
            } else {
                // If available, validate attestation alg and x5c with info in the metadata statement
                JsonObject statement = metadata.verifyMetadata(
                        authData.getAaguidString(),
                        PublicKeyCredential.valueOf(attStmt.getInteger("alg")),
                        null);

                if (statement != null) {
                    // The presence of x5c means this is a full attestation. Check to see if attestationTypes
                    // includes packed attestations.
                    if (!statement.getJsonArray("attestationTypes").contains(MetaData.ATTESTATION_BASIC_SURROGATE)) {
                        throw new AttestationException("Metadata does not indicate support for surrogate attestations");
                    }
                }

                // Self attestation is simple proof of key ownership,
                // that is produced by signing attestation with user’s
                // freshly generated private key.
                //
                // It used by the authenticators that don’t have memory
                // to store batch certificate and key pair.

                // Verifying attestation
                // 1. Concatenate authData with clientDataHash to create signatureBase
                byte[] signatureBase = Buffer.buffer()
                        .appendBytes(authData.getRaw())
                        .appendBytes(clientDataHash)
                        .getBytes();
                // 2. Parse authData and extract COSE public key
                JWK key = authData.getCredentialJWK();
                // 3. Verify signature “sig” over the signatureBase with the previously extracted public key.
                if (!key.verify(signature, signatureBase)) {
                    throw new AttestationException("Failed to verify the signature!");
                }
            }
        } catch (MetaDataException | CertificateException | InvalidKeyException | SignatureException | NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new AttestationException(e);
        }
    }
}
