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

import io.gravitee.am.gateway.handler.vertx.auth.jose.JWS;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.PublicKeyCredential;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthnOptions;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.AuthData;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.metadata.MetaData;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;

import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

// TODO to remove when updating to vert.x 4
public interface Attestation {

    /**
     * The unique identifier for the attestation
     * @return String
     */
    String fmt();

    /**
     * The implementation of the Attestation verification.
     *
     * @param options the runtime configuration options
     * @param metadata the Metadata holder to perform MDS queries
     * @param clientDataJSON the binary client data json
     * @param attestation the JSON representation of the attestation
     * @param authData the authenticator data
     *
     * @throws AttestationException if the validation fails
     */
    void validate(WebAuthnOptions options, MetaData metadata, byte[] clientDataJSON, JsonObject attestation, AuthData authData) throws AttestationException;

    /**
     * Returns SHA-256 digest of the given data.
     *
     * @param data - data to hash
     * @return the hash
     */
    static byte[] hash(final String algorithm, byte[] data) throws AttestationException, NoSuchAlgorithmException {
        if (algorithm == null || data == null) {
            throw new AttestationException("Cannot hash one of {algorithm, data} is null");
        }

        final MessageDigest md = MessageDigest.getInstance(algorithm);

        md.update(data);
        return md.digest();
    }

    /**
     * Verify if the data provider matches the signature based of the given certificate.
     *
     * @param certificate - origin certificate
     * @param signature   - received signature
     * @param data        - data to verify
     */
    static void verifySignature(PublicKeyCredential publicKeyCredential, X509Certificate certificate, byte[] signature, byte[] data) throws AttestationException, InvalidKeyException, SignatureException, InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        if (!JWS.verifySignature(publicKeyCredential.name(), certificate, signature, data)) {
            throw new AttestationException("Failed to verify signature");
        }
    }

    /**
     * Parses a JsonArray of certificates to a X509Certificate list
     * @param x5c the json array
     * @return list of X509Certificates
     */
    static List<X509Certificate> parseX5c(JsonArray x5c, Base64.Decoder b64dec) throws CertificateException {
        List<X509Certificate> certChain = new ArrayList<>();

        if (x5c == null || x5c.size() == 0) {
            return certChain;
        }

        for (int i = 0; i < x5c.size(); i++) {
            certChain.add(JWS.parseX5c(b64dec.decode(x5c.getString(i))));
        }

        return certChain;
    }
}
