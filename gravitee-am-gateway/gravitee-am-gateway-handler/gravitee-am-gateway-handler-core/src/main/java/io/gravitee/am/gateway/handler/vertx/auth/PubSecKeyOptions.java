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
package io.gravitee.am.gateway.handler.vertx.auth;

import io.vertx.core.json.JsonObject;

/**
 * Options describing Key stored in PEM format.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
// TODO to remove when updating to vert.x 4
public class PubSecKeyOptions {

    private String algorithm;
    private String buffer;
    private String id;

    private boolean certificate;
    private Boolean symmetric;
    private String publicKey;
    private String secretKey;

    /**
     * Default constructor
     */
    public PubSecKeyOptions() {
    }

    /**
     * Copy constructor
     *
     * @param other the options to copy
     */
    public PubSecKeyOptions(PubSecKeyOptions other) {
        algorithm = other.getAlgorithm();
        buffer = other.getBuffer();
        id = other.getId();
        publicKey = other.getPublicKey();
        secretKey = other.getSecretKey();
        symmetric = other.isSymmetric();
        certificate = other.isCertificate();
    }

    /**
     * Constructor to create an options from JSON
     *
     * @param json the JSON
     */
    public PubSecKeyOptions(JsonObject json) {
        PubSecKeyOptionsConverter.fromJson(json, this);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        PubSecKeyOptionsConverter.toJson(this, json);
        return json;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public PubSecKeyOptions setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    /**
     * The PEM or Secret key buffer
     * @return the buffer.
     */
    public String getBuffer() {
        return buffer;
    }

    /**
     * The PEM or Secret key buffer
     * @return self.
     */
    public PubSecKeyOptions setBuffer(String buffer) {
        this.buffer = buffer;
        return this;
    }

    public String getId() {
        return id;
    }

    public PubSecKeyOptions setId(String id) {
        this.id = id;
        return this;
    }

    @Deprecated
    public String getPublicKey() {
        return publicKey;
    }

    /**
     * @deprecated This setter ignored the PEM prefix and suffix which would assume the key to be RSA.
     *
     * Use {@link #setBuffer(String)} with the full content of your OpenSSL pem file. A PEM file must
     * contain at least 3 lines:
     *
     * <pre>
     *   -----BEGIN PUBLIC KEY----
     *   ...
     *   -----END PUBLIC KEY---
     * </pre>
     * @param publicKey the naked public key
     * @return self
     */
    @Deprecated
    public PubSecKeyOptions setPublicKey(String publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    @Deprecated
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * @deprecated This setter ignored the PEM prefix and suffix which would assume the key to be RSA.
     *
     * Use {@link #setBuffer(String)} with the full content of your OpenSSL pem file. A PEM file must
     * contain at least 3 lines:
     *
     * <pre>
     *   -----BEGIN PRIVATE KEY----
     *   ...
     *   -----END PRIVATE KEY---
     * </pre>
     * @param secretKey the naked public key
     * @return self
     */
    @Deprecated
    public PubSecKeyOptions setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        return this;
    }

    @Deprecated
    public boolean isSymmetric() {
        if (symmetric == null) {
            // attempt to derive the kind of key
            return algorithm.startsWith("HS") && publicKey == null && secretKey != null;
        }
        return symmetric;
    }

    @Deprecated
    public PubSecKeyOptions setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
        return this;
    }

    @Deprecated
    public boolean isCertificate() {
        return certificate;
    }

    @Deprecated
    public PubSecKeyOptions setCertificate(boolean certificate) {
        this.certificate = certificate;
        return this;
    }
}
