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
import io.gravitee.am.gateway.handler.vertx.auth.jose.JWS;
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

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.ASN1.*;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.attestation.Attestation.*;

/**
 * Implementation of the Apple attestation check.
 *
 * @author <a href="mailto:pmlopes@gmail.com>Paulo Lopes</a>
 */
// TODO to remove when updating to vert.x 4
public class AppleAttestation implements Attestation {

    @Override
    public String fmt() {
        return "apple";
    }

    @Override
    public void validate(WebAuthnOptions options, MetaData metadata, byte[] clientDataJSON, JsonObject attestation, AuthData authData) throws AttestationException {
        try {
            byte[] clientDataHash = hash("SHA-256", clientDataJSON);

            // Check attStmt and it contains “x5c” then its a FULL attestation.
            JsonObject attStmt = attestation.getJsonObject("attStmt");

            if (!attStmt.containsKey("x5c")) {
                throw new AttestationException("No attestation x5c");
            }

            List<X509Certificate> certChain = parseX5c(attStmt.getJsonArray("x5c"));

            if (certChain.size() == 0) {
                throw new AttestationException("no certificates in x5c field");
            }

            certChain.add(options.getRootCertificate(fmt()));

            // 1. Verify |x5c| is a valid certificate chain starting from the |credCert| to the Apple WebAuthn root certificate.
            CertificateHelper.checkValidity(certChain, true, options.getRootCrls());

            // 2. Concatenate |authenticatorData| and |clientDataHash| to form |nonceToHash|.
            byte[] nonceToHash = Buffer.buffer()
                    .appendBytes(authData.getRaw())
                    .appendBytes(clientDataHash)
                    .getBytes();

            // 3. Perform SHA-256 hash of |nonceToHash| to produce |nonce|.
            byte[] nonce = Attestation.hash("SHA-256", nonceToHash);

            // 4. Verify |nonce| matches the value of the extension with OID ( 1.2.840.113635.100.8.2 ) in |credCert|.
            final X509Certificate credCert = certChain.get(0);
            byte[] appleExtension = credCert.getExtensionValue("1.2.840.113635.100.8.2");
            ASN1.ASN extension = ASN1.parseASN1(appleExtension);
            if (extension.tag.type != OCTET_STRING) {
                throw new AttestationException("1.2.840.113635.100.8.2 Extension is not an ASN.1 OCTET string!");
            }
            // parse the octet as ASN.1 and expect it to se a sequence
            extension = parseASN1(extension.binary(0));
            if (extension.tag.type != SEQUENCE) {
                throw new AttestationException("1.2.840.113635.100.8.2 Extension is not an ASN.1 SEQUENCE!");
            }
            if (!MessageDigest.isEqual(nonce, extension.object(0).object(0).binary(0))) {
                throw new AttestationException("Certificate 1.2.840.113635.100.8.2 extension does not match nonce");
            }

            // 5. Verify credential public key matches the Subject Public Key of |credCert|.
            if (!credCert.getPublicKey().equals(authData.getCredentialJWK().getPublicKey())) {
                throw new AttestationException("credCert public key does not equal authData public key");
            }

            // meta data check
            metadata.verifyMetadata(
                    authData.getAaguidString(),
                    PublicKeyCredential.valueOf(attStmt.getInteger("alg")),
                    certChain);


        } catch (MetaDataException | CertificateException | InvalidKeyException | SignatureException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new AttestationException(e);
        }
    }
}
