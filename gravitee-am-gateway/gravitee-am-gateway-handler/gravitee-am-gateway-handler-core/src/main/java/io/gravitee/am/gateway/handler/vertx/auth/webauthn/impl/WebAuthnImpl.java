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

import com.fasterxml.jackson.core.JsonParser;
import io.gravitee.am.gateway.handler.vertx.auth.jose.JWK;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.AuthenticatorData;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.COSE;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthn;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.attestation.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.CredentialStore;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.ext.auth.webauthn.WebAuthnOptions;
import io.vertx.ext.auth.webauthn.impl.CBOR;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WebAuthnImpl implements WebAuthn {

    private static final Logger LOG = LoggerFactory.getLogger(WebAuthnImpl.class);

    // codecs
    private final Base64.Encoder b64enc = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder b64dec = Base64.getUrlDecoder();

    private final MessageDigest sha256;
    private final PRNG random;
    private final WebAuthnOptions options;
    private final CredentialStore store;

    private final Map<String, Attestation> attestations = new HashMap<>();

    public WebAuthnImpl(Vertx vertx, WebAuthnOptions options, CredentialStore store) {
        random = new PRNG(vertx);
        this.options = options;
        this.store = store;

        if (options == null || store == null) {
            throw new IllegalArgumentException("options and store cannot be null!");
        }

        // load attestations
        List<Attestation> attestationProviders = Arrays.asList(
                new AndroidKeyAttestation(),
                new AndroidSafetynetAttestation(),
                new FidoU2fAttestation(),
                new NoneAttestation(),
                new PackedAttestation(),
                new TPMAttestation()
        );
        for (Attestation att : attestationProviders) {
            attestations.put(att.fmt(), att);
        }
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException nsae) {
            throw new IllegalStateException("SHA-256 is not available", nsae);
        }
    }

    private String randomBase64URLBuffer(int length) {
        final byte[] buff = new byte[length];
        random.nextBytes(buff);
        return b64enc.encodeToString(buff);
    }

    @Override
    public WebAuthn createCredentialsOptions(JsonObject user, Handler<AsyncResult<JsonObject>> handler) {

        store.getUserCredentialsByName(user.getString("name"), getUserCredentials -> {
            if (getUserCredentials.failed()) {
                handler.handle(Future.failedFuture(getUserCredentials.cause()));
                return;
            }

            List<JsonObject> credentials = getUserCredentials.result();

            if (credentials == null || credentials.size() == 0) {
                // set the id of the user
                final String id = user.getString("id");

                final JsonArray pubKeyCredParams = new JsonArray();

                for (String pubKeyCredParam : options.getPubKeyCredParams()) {
                    switch (pubKeyCredParam) {
                        case "ES256":
                            pubKeyCredParams.add(
                                    new JsonObject()
                                            .put("type", "public-key")
                                            .put("alg", -7));
                            break;
                        case "ES384":
                            pubKeyCredParams.add(
                                    new JsonObject()
                                            .put("type", "public-key")
                                            .put("alg", -35));
                            break;
                        case "ES512":
                            pubKeyCredParams.add(
                                    new JsonObject()
                                            .put("type", "public-key")
                                            .put("alg", -36));
                            break;
                        case "RS256":
                            pubKeyCredParams.add(
                                    new JsonObject()
                                            .put("type", "public-key")
                                            .put("alg", -257));
                            break;
                        case "RS384":
                            pubKeyCredParams.add(
                                    new JsonObject()
                                            .put("type", "public-key")
                                            .put("alg", -258));
                            break;
                        case "RS512":
                            pubKeyCredParams.add(
                                    new JsonObject()
                                            .put("type", "public-key")
                                            .put("alg", -259));
                            break;
                        case "RS1":
                            pubKeyCredParams.add(
                                    new JsonObject()
                                            .put("type", "public-key")
                                            .put("alg", -65535));
                            break;
                        default:
                            LOG.warn("Unsupported algorithm: " + pubKeyCredParam);
                    }
                }

                // user configuration
                final JsonObject _user = new JsonObject()
                        .put("id", id)
                        .put("name", user.getString("name"))
                        .put("displayName", user.getString("displayName"));

                if (user.getString("icon") != null) {
                    _user.put("icon", user.getString("icon"));
                }

                final JsonObject authenticatorSelection;
                // authenticatorSelection configuration
                if (options.getAuthenticatorAttachment() != null) {
                    // server config takes precedence
                    authenticatorSelection = options.getAuthenticatorSelection();
                } else {
                    switch (user.getString("type")) {
                        case "cross-platform":
                        case "platform":
                            authenticatorSelection = new JsonObject()
                                    .put("authenticatorAttachment", user.getString("type"));

                            if (options.getRequireResidentKey() != null) {
                                authenticatorSelection.put("requireResidentKey", options.getRequireResidentKey());
                            }
                            if (options.getUserVerification() != null) {
                                authenticatorSelection.put("userVerification", options.getUserVerification().toString());
                            }
                            break;
                        default:
                            authenticatorSelection = null;
                    }
                }

                // final assembly
                final JsonObject publicKey = new JsonObject()
                        .put("challenge", randomBase64URLBuffer(options.getChallengeLength()))
                        .put("rp", options.getRelayParty().toJson())
                        .put("user", _user)
                        .put("pubKeyCredParams", pubKeyCredParams);

                if (authenticatorSelection != null) {
                    publicKey.put("authenticatorSelection", authenticatorSelection);
                }

                if (options.getAttestation() != null) {
                    publicKey.put("attestation", options.getAttestation().toString());
                }
                if (options.getTimeout() > 0) {
                    publicKey.put("timeout", options.getTimeout());
                }

                handler.handle(Future.succeededFuture(publicKey));
            } else {
                handler.handle(Future.failedFuture("User exists!"));
            }
        });
        return this;
    }

    @Override
    public WebAuthn getCredentialsOptions(String username, Handler<AsyncResult<JsonObject>> handler) {

        // we allow Resident Credentials or (RK) requests
        // this means that username is not required
        if (options.getRequireResidentKey() != null && options.getRequireResidentKey()) {
            if (username == null) {
                handler.handle(Future.succeededFuture(
                        new JsonObject()
                                .put("challenge", randomBase64URLBuffer(options.getChallengeLength()))));
                return this;
            }
        }

        // fallback to non RK requests

        store.getUserCredentialsByName(username, getUserCredentials -> {
            if (getUserCredentials.failed()) {
                handler.handle(Future.failedFuture(getUserCredentials.cause()));
                return;
            }

            List<JsonObject> credentials = getUserCredentials.result();

            if (credentials == null) {
                handler.handle(Future.failedFuture("Invalid username/account disabled."));
                return;
            }

            JsonArray allowCredentials = new JsonArray();

            JsonArray transports = new JsonArray();

            for (String transport : options.getTransports()) {
                transports.add(transport);
            }

            // STEP 19 Return allow credential ID
            for (JsonObject cred : credentials) {
                String credId = cred.getString("credID");
                if (credId != null) {
                    allowCredentials
                            .add(new JsonObject()
                                    .put("type", "public-key")
                                    .put("id", credId)
                                    .put("transports", transports));
                }
            }

            handler.handle(Future.succeededFuture(
                    new JsonObject()
                            .put("challenge", randomBase64URLBuffer(options.getChallengeLength()))
                            .put("allowCredentials", allowCredentials)
            ));
        });

        return this;
    }

    @Override
    public void authenticate(WebAuthnCredentials authInfo, Handler<AsyncResult<User>> handler) {
        //    {
        //      "rawId": "base64url",
        //      "id": "base64url",
        //      "response": {
        //        "attestationObject": "base64url",
        //        "clientDataJSON": "base64url"
        //      },
        //      "getClientExtensionResults": {},
        //      "type": "public-key"
        //    }
        final JsonObject webauthnResp = authInfo.getWebauthn();

        if (webauthnResp == null) {
            handler.handle(Future.failedFuture("webauthn can't be null!"));
            return;
        }

        // response can't be null
        final JsonObject response = webauthnResp.getJsonObject("response");

        if (response == null) {
            handler.handle(Future.failedFuture("wenauthn response can't be null!"));
            return;
        }

        byte[] clientDataJSON = b64dec.decode(response.getString("clientDataJSON"));
        JsonObject clientData = new JsonObject(Buffer.buffer(clientDataJSON));

        // Verify challenge is match with session
        if (!clientData.getString("challenge").equals(authInfo.getChallenge())) {
            handler.handle(Future.failedFuture("Challenges don't match!"));
            return;
        }

        // STEP 9 Verify origin is match with session
        if (!clientData.getString("origin").equals(options.getOrigin())) {
            handler.handle(Future.failedFuture("Origins don't match!"));
            return;
        }

        final String username = authInfo.getUsername();

        switch (clientData.getString("type")) {
            case "webauthn.create":
                // we always need a username to register
                if (username == null) {
                    handler.handle(Future.failedFuture("username can't be null!"));
                    return;
                }

                try {
                    final JsonObject authrInfo = verifyWebAuthNCreate(webauthnResp, clientDataJSON, clientData);
                    // the principal for vertx-auth
                    JsonObject principal = new JsonObject()
                            .put("credID", authrInfo.getString("credID"))
                            .put("publicKey", authrInfo.getString("publicKey"))
                            .put("counter", authrInfo.getLong("counter", 0L));

                    // by default the store can upsert if a credential is missing, the user has been verified so it is valid
                    // the store however might dissallow this operation
                    JsonObject storeItem = new JsonObject()
                            .mergeIn(principal)
                            .put("username", username)
                            .put("userId", webauthnResp.getString("userId"));

                    store.updateUserCredential(authrInfo.getString("credID"), storeItem, true, updateUserCredential -> {
                        if (updateUserCredential.failed()) {
                            handler.handle(Future.failedFuture(updateUserCredential.cause()));
                        } else {
                            handler.handle(Future.succeededFuture(new WebAuthnUser(principal)));
                        }
                    });
                } catch (RuntimeException | IOException e) {
                    handler.handle(Future.failedFuture(e));
                }
                return;
            case "webauthn.get":

                final Handler<AsyncResult<List<JsonObject>>> onGetUserCredentialsByAny = getUserCredentials -> {
                    if (getUserCredentials.failed()) {
                        handler.handle(Future.failedFuture(getUserCredentials.cause()));
                    } else {
                        List<JsonObject> authenticators = getUserCredentials.result();
                        if (authenticators == null) {
                            authenticators = Collections.emptyList();
                        }

                        // STEP 24 Query public key base on user ID
                        Optional<JsonObject> authenticator = authenticators.stream()
                                .filter(authr -> webauthnResp.getString("id").equals(authr.getValue("credID")))
                                .findFirst();

                        if (!authenticator.isPresent()) {
                            handler.handle(Future.failedFuture("Cannot find an authenticator with id: " + webauthnResp.getString("rawId")));
                            return;
                        }

                        try {
                            final JsonObject json = authenticator.get();
                            final long counter = verifyWebAuthNGet(webauthnResp, clientDataJSON, clientData, json);
                            // update the counter on the authenticator
                            json.put("counter", counter);
                            // update the credential (the important here is to update the counter)
                            store.updateUserCredential(webauthnResp.getString("rawId"), json, false, updateUserCredential -> {
                                if (updateUserCredential.failed()) {
                                    handler.handle(Future.failedFuture(updateUserCredential.cause()));
                                    return;
                                }
                                handler.handle(Future.succeededFuture(new WebAuthnUser(json)));
                            });
                        } catch (RuntimeException | IOException e) {
                            handler.handle(Future.failedFuture(e));
                        }
                    }
                };

                if (options.getRequireResidentKey() != null && options.getRequireResidentKey()) {
                    // username are not provided (RK) we now need to lookup by rawId
                    store.getUserCredentialsById(webauthnResp.getString("rawId"), onGetUserCredentialsByAny);

                } else {
                    // username can't be null
                    if (username == null) {
                        handler.handle(Future.failedFuture("username can't be null!"));
                        return;
                    }
                    store.getUserCredentialsByName(username, onGetUserCredentialsByAny);
                }

                return;
            default:
                handler.handle(Future.failedFuture("Can not determine type of response!"));
        }
    }

    /**
     * Verify creadentials from client
     *
     * @param webAuthnResponse - Data from navigator.credentials.create
     */
    private JsonObject verifyWebAuthNCreate(JsonObject webAuthnResponse, byte[] clientDataJSON, JsonObject clientData) throws AttestationException, IOException {
        JsonObject response = webAuthnResponse.getJsonObject("response");
        // STEP 11 Extract attestation Object
        try (JsonParser parser = CBOR.cborParser(response.getString("attestationObject"))) {
            //      {
            //        "fmt": "fido-u2f",
            //        "authData": "cbor",
            //        "attStmt": {
            //          "sig": "cbor",
            //          "x5c": [
            //            "cbor"
            //          ]
            //        }
            //      }
            JsonObject ctapMakeCredResp = new JsonObject(CBOR.<Map>parse(parser));
            // STEP 12 Extract auth data
            AuthenticatorData authrDataStruct = new AuthenticatorData(ctapMakeCredResp.getString("authData"));
            // STEP 13 Extract public key
            byte[] publicKey = authrDataStruct.getCredentialPublicKey();

            final String fmt = ctapMakeCredResp.getString("fmt");

            // STEP 14 Verify attestation based on type of device
            final Attestation attestation = attestations.get(fmt);

            if (attestation == null) {
                throw new AttestationException("Unknown attestation fmt: " + fmt);
            } else {
                // perform the verification
                attestation.verify(webAuthnResponse, clientDataJSON, ctapMakeCredResp, authrDataStruct);
            }

            // STEP 15 Create data for save to database
            return new JsonObject()
                    .put("fmt", fmt)
                    .put("publicKey", b64enc.encodeToString(publicKey))
                    .put("counter", authrDataStruct.getSignCounter())
                    .put("credID", b64enc.encodeToString(authrDataStruct.getCredentialId()));
        }
    }

    /**
     * @param webAuthnResponse - Data from navigator.credentials.get
     * @param authr            - Credential from Database
     */
    private long verifyWebAuthNGet(JsonObject webAuthnResponse, byte[] clientDataJSON, JsonObject clientData, JsonObject authr) throws IOException, AttestationException {

        JsonObject response = webAuthnResponse.getJsonObject("response");

        // STEP 25 parse auth data
        byte[] authenticatorData = b64dec.decode(response.getString("authenticatorData"));
        AuthenticatorData authrDataStruct = new AuthenticatorData(authenticatorData);

        if (!authrDataStruct.is(AuthenticatorData.USER_PRESENT)) {
            throw new RuntimeException("User was NOT present durring authentication!");
        }

        // TODO: assert the algorithm to be SHA-256 clientData.getString("hashAlgorithm") ?

        // STEP 26 hash clientDataJSON with sha256
        byte[] clientDataHash = hash(clientDataJSON);
        // STEP 27 create signature base by concat authenticatorData and clientDataHash
        Buffer signatureBase = Buffer.buffer()
                .appendBytes(authenticatorData)
                .appendBytes(clientDataHash);

        // STEP 28 format public key
        try (JsonParser parser = CBOR.cborParser(authr.getString("publicKey"))) {
            // the decoded credential primary as a JWK
            JWK publicKey = COSE.toJWK(CBOR.parse(parser));

            // STEP 29 convert signature to buffer
            byte[] signature = b64dec.decode(response.getString("signature"));

            // STEP 30 verify signature
            boolean verified = publicKey.verify(signature, signatureBase.getBytes());

            if (!verified) {
                throw new AttestationException("Failed to verify the signature!");
            }

            if (authrDataStruct.getSignCounter() <= authr.getLong("counter")) {
                throw new AttestationException("Authr counter did not increase!");
            }

            // return the counter so it can be updated on the store
            return authrDataStruct.getSignCounter();
        }
    }

    private byte[] hash(byte[] data) {
        synchronized (sha256) {
            sha256.update(data);
            return sha256.digest();
        }
    }

    private class WebAuthnUser implements User {

        private final JsonObject principal;

        private WebAuthnUser(JsonObject principal) {
            this.principal = principal;
        }

        @Override
        public User isAuthorized(String authority, Handler<AsyncResult<Boolean>> resultHandler) {
            return null;
        }

        @Override
        public User clearCache() {
            return null;
        }

        @Override
        public JsonObject principal() {
            return principal;
        }

        @Override
        public void setAuthProvider(AuthProvider authProvider) {
        }
    }
}
