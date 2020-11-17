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

import com.fasterxml.jackson.core.JsonParser;
import io.gravitee.am.gateway.handler.vertx.auth.CertificateHelper;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.PublicKeyCredential;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthnOptions;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.AuthData;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.metadata.MetaData;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.impl.CBOR;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.attestation.Attestation.*;

/**
 * Implementation of the "fido-u2f" attestation check.
 * <p>
 * This attestation verifies the hardware for fido-u2f hardware tokens such
 * as the yubikey.
 *
 * @author <a href="mailto:pmlopes@gmail.com>Paulo Lopes</a>
 */
// TODO to remove when updating to vert.x 4
public class FidoU2fAttestation implements Attestation {

    @Override
    public String fmt() {
        return "fido-u2f";
    }

    @Override
    public void validate(WebAuthnOptions options, MetaData metadata, byte[] clientDataJSON, JsonObject attestation, AuthData authData) throws AttestationException {
        // the attestation object should have the following structure:
        //{
        //    "fmt": "fido-u2f",
        //    "authData": "base64",
        //    "attStmt": {
        //        "sig": "base64",
        //        "x5c": [
        //            "base64"
        //        ]
        //    }
        //}

        try {
            // AAGUID must be null
            if (!"00000000-0000-0000-0000-000000000000".equals(authData.getAaguidString())) {
                throw new AttestationException("AAGUID is not 00000000-0000-0000-0000-000000000000!");
            }

            byte[] clientDataHash = hash("SHA-256", clientDataJSON);

            // FIDO stores public keys in ANSI format
            byte[] publicKey = COSEECDHAtoPKCS(authData.getCredentialPublicKey());

            // in order to verify signature we need to reconstruct
            // the original signatureBase buffer. To do that we need:
            Buffer signatureBase = Buffer.buffer()
                    .appendByte((byte) 0x00) // reserved byte
                    .appendBytes(authData.getRpIdHash())
                    .appendBytes(clientDataHash)
                    .appendBytes(authData.getCredentialId())
                    .appendBytes(publicKey);

            JsonObject attStmt = attestation.getJsonObject("attStmt");

            List<X509Certificate> certChain = parseX5c(attStmt.getJsonArray("x5c"));
            if (certChain.size() == 0) {
                throw new AttestationException("no certificates in x5c field");
            }
            // validate the chain
            CertificateHelper.checkValidity(certChain, options.getRootCrls());
            // certificate valid lets verify signatures
            verifySignature(
                    PublicKeyCredential.ES256,
                    certChain.get(0),
                    attStmt.getBinary("sig"),
                    signatureBase.getBytes());

        } catch (CertificateException | InvalidKeyException | SignatureException | NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new AttestationException(e);
        }
    }

    /**
     * Takes COSE encoded public key and converts it to RAW PKCS ECDHA key
     *
     * @param cosePublicKey - COSE encoded public key
     * @return - RAW PKCS encoded public key
     */
    private static byte[] COSEECDHAtoPKCS(byte[] cosePublicKey) {
      /*
         +------+-------+-------+---------+----------------------------------+
         | name | key   | label | type    | description                      |
         |      | type  |       |         |                                  |
         +------+-------+-------+---------+----------------------------------+
         | crv  | 2     | -1    | int /   | EC Curve identifier - Taken from |
         |      |       |       | tstr    | the COSE Curves registry         |
         |      |       |       |         |                                  |
         | x    | 2     | -2    | bstr    | X Coordinate                     |
         |      |       |       |         |                                  |
         | y    | 2     | -3    | bstr /  | Y Coordinate                     |
         |      |       |       | bool    |                                  |
         |      |       |       |         |                                  |
         | d    | 2     | -4    | bstr    | Private key                      |
         +------+-------+-------+---------+----------------------------------+
      */
        try (JsonParser parser = CBOR.cborParser(cosePublicKey)) {
            JsonObject key = new JsonObject(CBOR.<Map<String, Object>>parse(parser));

            return Buffer.buffer()
                    .appendByte((byte) 0x04)
                    .appendBytes(key.getBinary("-2"))
                    .appendBytes(key.getBinary("-3"))
                    .getBytes();
        } catch (IOException e) {
            throw new DecodeException(e.getMessage());
        }
    }
}
