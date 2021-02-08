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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.util.Base64;
import java.util.UUID;

/**
 * Data Object representing an authenticator at rest.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
// TODO to remove when updating to vert.x 4
public class Authenticator {

    /**
     * The username linked to this authenticator
     */
    private String userName;

    /**
     * The type of key (must be "public-key")
     */
    private String type = "public-key";

    /**
     * The non user identifiable id for the authenticator
     */
    private String credID;

    /**
     * The public key associated with this authenticator
     */
    private String publicKey;

    /**
     * The signature counter of the authenticator to prevent replay attacks
     */
    private long counter;

    /**
     * The AAGUID of the authenticator.
     */
    private String aaguid;

    /**
     * Attestation statement format identifier
     */
    private String attestationStatementFormat;

    /**
     * The attestation statement structure
     */
    private String attestationStatement;

    public Authenticator() {}

    public Authenticator(JsonObject json) {
        AuthenticatorConverter.fromJson(json, this);
    }

    public String getUserName() {
        return userName;
    }

    public Authenticator setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getType() {
        return type;
    }

    public Authenticator setType(String type) {
        this.type = type;
        return this;
    }

    public String getCredID() {
        return credID;
    }

    public Authenticator setCredID(String credID) {
        this.credID = credID;
        return this;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public Authenticator setPublicKey(String publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    public long getCounter() {
        return counter;
    }

    public Authenticator setCounter(long counter) {
        this.counter = counter;
        return this;
    }

    public String getAaguid() {
        return aaguid;
    }

    public Authenticator setAaguid(String aaguid) {
        this.aaguid = aaguid;
        return this;
    }

    public String getAttestationStatementFormat() {
        return attestationStatementFormat;
    }

    public Authenticator setAttestationStatementFormat(String attestationStatementFormat) {
        this.attestationStatementFormat = attestationStatementFormat;
        return this;
    }

    public String getAttestationStatement() {
        return attestationStatement;
    }

    public Authenticator setAttestationStatement(String attestationStatement) {
        this.attestationStatement = attestationStatement;
        return this;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        AuthenticatorConverter.toJson(this, json);
        return json;
    }

    public static UUID toUUID(String string) {
        if (string == null) {
            return null;
        }
        Buffer buffer = Buffer.buffer(Base64.getUrlDecoder().decode(string));
        return new UUID(buffer.getLong(0), buffer.getLong(8));
    }

    @Override
    public String toString() {
        return toJson().encode();
    }
}
