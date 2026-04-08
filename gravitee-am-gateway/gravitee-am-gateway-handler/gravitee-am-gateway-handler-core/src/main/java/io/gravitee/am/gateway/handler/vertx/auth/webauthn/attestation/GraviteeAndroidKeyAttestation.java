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

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.GraviteeWebAuthnOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.AttestationCertificates;
import io.vertx.ext.auth.webauthn.PublicKeyCredential;
import io.vertx.ext.auth.webauthn.WebAuthnOptions;
import io.vertx.ext.auth.webauthn.impl.AuthData;
import io.vertx.ext.auth.impl.jose.JWK;
import io.vertx.ext.auth.webauthn.impl.attestation.Attestation;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;
import io.vertx.ext.auth.webauthn.impl.metadata.MetaData;
import io.vertx.ext.auth.webauthn.impl.metadata.MetaDataException;
import lombok.extern.slf4j.Slf4j;

import java.security.*;
import java.security.cert.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.vertx.ext.auth.impl.Codec.base64UrlDecode;
import static io.vertx.ext.auth.webauthn.impl.attestation.Attestation.*;
import static io.vertx.ext.auth.impl.asn.ASN1.*;
import static io.vertx.ext.auth.webauthn.impl.metadata.MetaData.*;

/**
 * Implementation of the "android-key" attestation check.
 * <p>
 * Android KeyStore is a key management container, that defends key material from extraction.
 * Depending on the device, it can be either software or hardware backed.
 * <p>
 * For example if authenticator required to be FIPS/CC/PCI/FIDO compliant, then it needs
 * to be running on the device with FIPS/CC/PCI/FIDO compliant hardware, and it can be
 * found getting KeyStore attestation.
 *
 * @author <a href="mailto:pmlopes@gmail.com>Paulo Lopes</a>
 *
 * This specific implementation is a copy of vertx codebase with a workaround
 * to manage multiple root CA. This is to avoid issue since in june 2025 the CA
 * has changed and old device are not able to use the new one.
 *
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class GraviteeAndroidKeyAttestation implements Attestation {

    private static final JsonArray EMPTY = new JsonArray(Collections.emptyList());

    @Override
    public String fmt() {
        return "android-key";
    }

    @Override
    public AttestationCertificates validate(WebAuthnOptions options, MetaData metadata, byte[] clientDataJSON, JsonObject attestation, AuthData authData) throws AttestationException {
        // Typical attestation object
        //{
        //    "fmt": "android-key",
        //    "authData": "base64",
        //    "attStmt": {
        //        "alg": -7,
        //        "sig": "base64",
        //        "x5c": [
        //            "base64",
        //            "base64",
        //            "base64"
        //        ]
        //    }
        //}

        try {
            byte[] clientDataHash = Attestation.hash("SHA-256", clientDataJSON);

            // Verifying attestation
            // 1. Concatenate authData with clientDataHash to create signatureBase
            byte[] signatureBase = Buffer.buffer()
                    .appendBytes(authData.getRaw())
                    .appendBytes(clientDataHash)
                    .getBytes();
            // 2. Verify signature sig over the signatureBase using
            //    public key extracted from leaf certificate in x5c
            JsonObject attStmt = attestation.getJsonObject("attStmt");
            byte[] signature = base64UrlDecode(attStmt.getString("sig"));
            List<X509Certificate> certChain = parseX5c(attStmt.getJsonArray("x5c"));
            if (certChain.size() == 0) {
                throw new AttestationException("Invalid certificate chain");
            }

            final X509Certificate leafCert = certChain.get(0);

            // verify the signature
            verifySignature(
                    PublicKeyCredential.valueOf(attStmt.getInteger("alg")),
                    leafCert,
                    signature,
                    signatureBase);

            // meta data check
            JsonObject statement = metadata.verifyMetadata(
                    authData.getAaguidString(),
                    PublicKeyCredential.valueOf(attStmt.getInteger("alg")),
                    certChain);

            // Verifying attestation certificate
            // 1. Check that authData publicKey matches the public key in the attestation certificate
            JWK coseKey = authData.getCredentialJWK();
            if (!leafCert.getPublicKey().equals(coseKey.publicKey())) {
                throw new AttestationException("Certificate public key does not match public key in authData!");
            }
            // 2. Find Android KeyStore Extension with OID “1.3.6.1.4.1.11129.2.1.17” in certificate extensions.
            ASN attestationExtension = parseASN1(Buffer.buffer(leafCert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")));
            if (!attestationExtension.is(OCTET_STRING)) {
                throw new AttestationException("Attestation Extension is not an ASN.1 OCTECT string!");
            }
            // parse the octec as ASN.1 and expect it to se a sequence
            attestationExtension = parseASN1(Buffer.buffer(attestationExtension.binary(0)));
            if (!attestationExtension.is(SEQUENCE)) {
                throw new AttestationException("Attestation Extension Value is not an ASN.1 SEQUENCE!");
            }
            // get the data at index 4 (certificate challenge)
            byte[] data = attestationExtension.object(4).binary(0);

            // 3. Check that attestationChallenge is set to the clientDataHash.
            // verify that the client hash matches the certificate hash
            if (!MessageDigest.isEqual(clientDataHash, data)) {
                throw new AttestationException("Certificate attestation challenge is not set to the clientData hash!");
            }
            // 4. Check that both teeEnforced and softwareEnforced structures don’t contain allApplications(600) tag.
            // This is important as the key must strictly bound to the caller app identifier.
            ASN softwareEnforcedAuthz = attestationExtension.object(6);
            for (Object object : softwareEnforcedAuthz.value) {
                if (object instanceof ASN) {
                    // verify if the that the list doesn't contain "allApplication" 600 flag
                    if (((ASN) object).tag.number == 600) {
                        throw new AttestationException("Software authorisation list contains 'allApplication' flag, which means that credential is not bound to the RP!");
                    }
                }
            }
            // 4. Check that both teeEnforced and softwareEnforced structures don’t contain allApplications(600) tag.
            // This is important as the key must strictly bound to the caller app identifier.
            ASN teeEnforcedAuthz = attestationExtension.object(7);
            for (Object object : teeEnforcedAuthz.value) {
                if (object instanceof ASN) {
                    // verify if the that the list doesn't contain "allApplication" 600 flag
                    if (((ASN) object).tag.number == 600) {
                        throw new AttestationException("TEE authorisation list contains 'allApplication' flag, which means that credential is not bound to the RP!");
                    }
                }
            }

            if (statement == null || statement.getJsonArray("attestationRootCertificates", EMPTY).size() == 0) {
                // 5. Validate the certificate chain against the configured trust anchors.
                // Builds a PKIX path from x5c and validates it against the default root plus
                // any additional roots provided by GraviteeWebAuthnOptions. This handles both
                // chains that include the trust anchor as last element and chains where the
                // device omits the root (RKP-enabled Android devices from 2026-04-10).
                validateCertificateChain(options, certChain);
            }

            if (statement != null) {
                // verify that the statement allows this type of attestation
                if (!statementAttestationTypesContains(statement, ATTESTATION_ANONCA)) {
                    throw new AttestationException("Metadata does not indicate support for anonca attestations");
                }
            }

            return new AttestationCertificates()
                    .setAlg(PublicKeyCredential.valueOf(attStmt.getInteger("alg")))
                    .setX5c(attStmt.getJsonArray("x5c"));

        } catch (MetaDataException | CertificateException | InvalidKeyException | SignatureException | NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new AttestationException(e);
        }
    }

    /**
     * Validate the attestation certificate chain against the configured trust anchors
     * using PKIX path validation. Trust anchors are collected from the default root
     * registered on {@link WebAuthnOptions} and any additional roots exposed by
     * {@link GraviteeWebAuthnOptions#getAdditionalRootCertificate(String)}.
     * <p>
     * The cert path passed to {@link CertPathValidator} must NOT include the trust
     * anchor itself, so a self-signed last element is stripped. Revocation checking
     * is disabled because attestation roots are validated offline (no CRL/OCSP).
     */
    private void validateCertificateChain(WebAuthnOptions options, List<X509Certificate> certChain)
            throws AttestationException {
        // Collect trust anchors from default + additional roots
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        X509Certificate defaultRoot = options.getRootCertificate(fmt());
        if (defaultRoot != null) {
            trustAnchors.add(new TrustAnchor(defaultRoot, null));
        }
        if (options instanceof GraviteeWebAuthnOptions) {
            List<X509Certificate> extra = ((GraviteeWebAuthnOptions) options).getAdditionalRootCertificate(fmt());
            if (extra != null) {
                for (X509Certificate c : extra) {
                    trustAnchors.add(new TrustAnchor(c, null));
                }
            }
        }
        if (trustAnchors.isEmpty()) {
            throw new AttestationException("No trust anchors configured for " + fmt());
        }

        // Strip a self-signed trailing cert: CertPath must not contain the trust anchor.
        List<X509Certificate> path = new ArrayList<>(certChain);
        X509Certificate last = path.get(path.size() - 1);
        if (last.getIssuerX500Principal().equals(last.getSubjectX500Principal())) {
            path.remove(path.size() - 1);
        }

        // Edge case: chain was just a self-signed root. Accept iff it matches a trust anchor.
        if (path.isEmpty()) {
            for (TrustAnchor ta : trustAnchors) {
                if (ta.getTrustedCert().equals(last)) {
                    return;
                }
            }
            throw new AttestationException("Root certificate is invalid: self-signed leaf is not a trusted anchor");
        }

        try {
            CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(path);
            PKIXParameters params = new PKIXParameters(trustAnchors);
            params.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(certPath, params);
        } catch (CertPathValidatorException e) {
            throw new AttestationException("Root certificate is invalid: " + e.getMessage());
        } catch (CertificateException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            throw new AttestationException("Failed to validate attestation certificate chain: " + e.getMessage());
        }
    }
}
