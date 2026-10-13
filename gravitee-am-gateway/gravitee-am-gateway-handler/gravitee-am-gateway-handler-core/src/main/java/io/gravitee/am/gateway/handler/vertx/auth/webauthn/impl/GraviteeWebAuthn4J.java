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

import com.webauthn4j.async.WebAuthnAsyncManager;
import com.webauthn4j.async.verifier.attestation.statement.androidkey.AndroidKeyAttestationStatementAsyncVerifier;
import com.webauthn4j.async.verifier.attestation.statement.androidsafetynet.AndroidSafetyNetAttestationStatementAsyncVerifier;
import com.webauthn4j.async.verifier.attestation.statement.apple.AppleAnonymousAttestationStatementAsyncVerifier;
import com.webauthn4j.async.verifier.attestation.statement.none.NoneAttestationStatementAsyncVerifier;
import com.webauthn4j.async.verifier.attestation.statement.packed.PackedAttestationStatementAsyncVerifier;
import com.webauthn4j.async.verifier.attestation.statement.tpm.TPMAttestationStatementAsyncVerifier;
import com.webauthn4j.async.verifier.attestation.statement.u2f.FIDOU2FAttestationStatementAsyncVerifier;
import com.webauthn4j.async.verifier.attestation.trustworthiness.self.DefaultSelfAttestationTrustworthinessAsyncVerifier;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.AndroidKeyAttestationStatement;
import com.webauthn4j.data.attestation.statement.AndroidSafetyNetAttestationStatement;
import com.webauthn4j.data.attestation.statement.AttestationCertificatePath;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement;
import com.webauthn4j.data.attestation.statement.FIDOU2FAttestationStatement;
import com.webauthn4j.data.attestation.statement.PackedAttestationStatement;
import com.webauthn4j.data.attestation.statement.TPMAttestationStatement;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.UserNotVerifiedException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.CredentialValidationException;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.prng.VertxContextPRNG;
import io.vertx.ext.auth.webauthn4j.AttestationCertificates;
import io.vertx.ext.auth.webauthn4j.Authenticator;
import io.vertx.ext.auth.webauthn4j.AuthenticatorTransport;
import io.vertx.ext.auth.webauthn4j.COSEAlgorithm;
import io.vertx.ext.auth.webauthn4j.CredentialStorage;
import io.vertx.ext.auth.webauthn4j.ResidentKey;
import io.vertx.ext.auth.webauthn4j.UserVerification;
import io.vertx.ext.auth.webauthn4j.WebAuthn4J;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JCredentials;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JException;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JOptions;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static io.vertx.ext.auth.impl.Codec.base64UrlDecode;
import static io.vertx.ext.auth.impl.Codec.base64UrlEncode;

/**
 * The {@link WebAuthn4J} implementation of AM, forked from {@code WebAuthn4JImpl} of vertx-auth-webauthn4j
 * 5.0.12 to keep the behavior AM had with vertx-auth-webauthn 5.0.0.CR2:
 * <ul>
 *     <li>every attestation format is verified whatever the attestation preference, {@code none} included,
 *     so passkeys keep registering on domains asking for a DIRECT or INDIRECT attestation;</li>
 *     <li>trust is decided per format by {@link FormatScopedCertPathTrustworthinessVerifier} on the roots
 *     given to the constructor: the root certificates of {@link WebAuthn4JOptions} are not used, nor the
 *     FIDO Metadata Service;</li>
 *     <li>Android Key accepts keys that are not hardware-backed;</li>
 *     <li>user verification REQUIRED means user verified or present at registration, verified and present
 *     at authentication; nothing is enforced otherwise;</li>
 *     <li>a signature counter presented as 0 is accepted, and stored; any other value must increase;</li>
 *     <li>the relying party id verified is {@link WebAuthn4JCredentials#getDomain()} when set, so it follows
 *     the entrypoint of the request, then the relying party id of the options, then the origin host;</li>
 *     <li>with a required resident key the credential is looked up by its id only;</li>
 *     <li>client extension results and transports are not read.</li>
 * </ul>
 *
 * @author GraviteeSource Team
 */
public class GraviteeWebAuthn4J implements WebAuthn4J {

    private static final byte FLAG_USER_PRESENT = 0x01;

    private final VertxContextPRNG random;
    private final WebAuthn4JOptions options;
    private final WebAuthnAsyncManager webAuthnManager;
    private final ObjectConverter objectConverter = new ObjectConverter();

    private CredentialStorage credentialStorage;

    /**
     * @param rootsByFormat the trusted roots of each anchored attestation format
     */
    public GraviteeWebAuthn4J(Vertx vertx, WebAuthn4JOptions options, Map<String, List<X509Certificate>> rootsByFormat) {
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null!");
        }
        if (options.getRelyingParty() == null) {
            throw new IllegalArgumentException("options.relyingParty cannot be null!");
        }
        if (options.getRelyingParty().getName() == null) {
            throw new IllegalArgumentException("options.relyingParty.name cannot be null!");
        }
        this.random = VertxContextPRNG.current(vertx);
        this.options = options;

        AndroidKeyAttestationStatementAsyncVerifier androidKeyVerifier = new AndroidKeyAttestationStatementAsyncVerifier();
        androidKeyVerifier.setTeeEnforcedOnly(false);
        this.webAuthnManager = new WebAuthnAsyncManager(
                List.of(
                        new NoneAttestationStatementAsyncVerifier(),
                        new FIDOU2FAttestationStatementAsyncVerifier(),
                        new PackedAttestationStatementAsyncVerifier(),
                        new TPMAttestationStatementAsyncVerifier(),
                        androidKeyVerifier,
                        new AndroidSafetyNetAttestationStatementAsyncVerifier(),
                        new AppleAnonymousAttestationStatementAsyncVerifier()),
                new FormatScopedCertPathTrustworthinessVerifier(rootsByFormat),
                new DefaultSelfAttestationTrustworthinessAsyncVerifier(),
                objectConverter);
    }

    @Override
    public WebAuthn4J credentialStorage(CredentialStorage credentialStorage) {
        if (credentialStorage == null) {
            throw new IllegalArgumentException("CredentialStorage cannot be null");
        }
        this.credentialStorage = credentialStorage;
        return this;
    }

    @Override
    public Future<JsonObject> createCredentialsOptions(JsonObject user) {
        return credentialStorage.find(user.getString("name"), null)
                .map(authenticators -> {
                    // empty structure with all required fields
                    JsonObject json = new JsonObject()
                            .put("rp", new JsonObject())
                            .put("user", new JsonObject())
                            .put("challenge", randomBase64URLBuffer(options.getChallengeLength()))
                            .put("pubKeyCredParams", new JsonArray())
                            .put("authenticatorSelection", new JsonObject());

                    putOpt(json.getJsonObject("rp"), "id", options.getRelyingParty().getId());
                    putOpt(json.getJsonObject("rp"), "name", options.getRelyingParty().getName());

                    putOpt(json.getJsonObject("user"), "id", uuidToBase64Url(UUID.randomUUID()));
                    putOpt(json.getJsonObject("user"), "name", user.getString("name"));
                    putOpt(json.getJsonObject("user"), "displayName", user.getString("displayName"));
                    putOpt(json.getJsonObject("user"), "icon", user.getString("icon"));
                    for (COSEAlgorithm pubKeyCredParam : options.getPubKeyCredParams()) {
                        addOpt(json.getJsonArray("pubKeyCredParams"), new JsonObject()
                                .put("alg", pubKeyCredParam.coseId())
                                .put("type", "public-key"));
                    }
                    putOpt(json, "timeout", options.getTimeoutInMilliseconds());
                    if (!authenticators.isEmpty()) {
                        JsonArray excludeCredentials = new JsonArray();
                        for (Authenticator key : authenticators) {
                            JsonObject credentialDescriptor = new JsonObject()
                                    .put("type", key.getType())
                                    .put("id", key.getCredID());
                            putOpt(credentialDescriptor, "transports", transports());
                            addOpt(excludeCredentials, credentialDescriptor);
                        }
                        putOpt(json, "excludeCredentials", excludeCredentials);
                    }
                    putOpt(json.getJsonObject("authenticatorSelection"), "authenticatorAttachment", options.getAuthenticatorAttachment());
                    putOpt(json.getJsonObject("authenticatorSelection"), "residentKey", options.getResidentKey());
                    putOpt(json.getJsonObject("authenticatorSelection"), "requireResidentKey", options.getResidentKey() == ResidentKey.REQUIRED);
                    putOpt(json.getJsonObject("authenticatorSelection"), "userVerification", options.getUserVerification());
                    putOpt(json, "attestation", options.getAttestation());
                    putOpt(json, "extensions", options.getExtensions());
                    return json;
                });
    }

    @Override
    public Future<JsonObject> getCredentialsOptions(String name) {
        // https://w3c.github.io/webauthn/#dictionary-assertion-options
        JsonObject json = new JsonObject()
                .put("challenge", randomBase64URLBuffer(options.getChallengeLength()));
        putOpt(json, "timeout", options.getTimeoutInMilliseconds());
        putOpt(json, "rpId", options.getRelyingParty().getId());
        putOpt(json, "userVerification", options.getUserVerification());
        putOpt(json, "extensions", options.getExtensions());

        switch (options.getResidentKey()) {
            case REQUIRED:
            case PREFERRED:
                // discoverable credentials: the browser lists them, no name needed
                return Future.succeededFuture(json);
            case DISCOURAGED:
            default:
                if (name == null) {
                    return Future.failedFuture("Name is required for non RK requests");
                }
        }
        return credentialStorage.find(name, null)
                .compose(authenticators -> authenticators.isEmpty()
                        ? Future.<List<Authenticator>>failedFuture("No authenticators registered for user: " + name)
                        : Future.succeededFuture(authenticators))
                .map(authenticators -> {
                    JsonArray allowCredentials = new JsonArray();
                    for (Authenticator key : authenticators) {
                        if (key.getCredID() != null) {
                            JsonObject credential = new JsonObject()
                                    .put("type", "public-key")
                                    .put("id", key.getCredID());
                            putOpt(credential, "transports", transports());
                            allowCredentials.add(credential);
                        }
                    }
                    putOpt(json, "allowCredentials", allowCredentials);
                    return json;
                });
    }

    @Override
    public Future<User> authenticate(Credentials credentials) {
        try {
            WebAuthn4JCredentials authInfo;
            try {
                authInfo = (WebAuthn4JCredentials) credentials;
            } catch (ClassCastException e) {
                throw new CredentialValidationException("Invalid credentials type", e);
            }
            authInfo.checkValid(null);

            final JsonObject webauthn = authInfo.getWebauthn();
            final JsonObject response = webauthn.getJsonObject("response");
            final byte[] clientDataJSON = base64UrlDecode(response.getString("clientDataJSON"));
            final JsonObject clientData = new JsonObject(Buffer.buffer(clientDataJSON));

            if (!authInfo.getChallenge().equals(clientData.getString("challenge"))) {
                return Future.failedFuture("Challenges don't match!");
            }
            if (authInfo.getOrigin() != null && !authInfo.getOrigin().equals(clientData.getString("origin"))) {
                return Future.failedFuture("Origins don't match!");
            }
            if (!clientData.containsKey("type")) {
                return Future.failedFuture("Missing type on client data");
            }

            final String username = authInfo.getUsername();
            switch (clientData.getString("type")) {
                case "webauthn.create":
                    if (username == null) {
                        return Future.failedFuture("username can't be null!");
                    }
                    return verifyWebAuthnCreate(response, authInfo, clientDataJSON)
                            .compose(authenticator -> {
                                authenticator.setUsername(username);
                                return credentialStorage.storeCredential(authenticator)
                                        .map(stored -> toUser(authenticator));
                            });
                case "webauthn.get":
                    final String credentialId = webauthn.getString("id");
                    final String lookupName;
                    switch (options.getResidentKey()) {
                        case REQUIRED:
                            // the browser picked a discoverable credential: look it up by id only
                            lookupName = null;
                            break;
                        case PREFERRED:
                            lookupName = username;
                            break;
                        case DISCOURAGED:
                        default:
                            if (username == null) {
                                return Future.failedFuture("username can't be null!");
                            }
                            lookupName = username;
                    }
                    return credentialStorage.find(lookupName, credentialId)
                            .compose(authenticators -> {
                                // the list may hold every credential of the user: pick the one used
                                Authenticator authenticator = authenticators == null ? null : authenticators.stream()
                                        .filter(candidate -> credentialId.equals(candidate.getCredID()))
                                        .findFirst()
                                        .orElse(null);
                                if (authenticator == null) {
                                    return Future.failedFuture("Cannot find authenticator with id: " + credentialId);
                                }
                                return verifyWebAuthnGet(response, authInfo, clientDataJSON, authenticator)
                                        .compose(counter -> {
                                            authenticator.setCounter(counter);
                                            return credentialStorage.updateCounter(authenticator)
                                                    .map(stored -> toUser(authenticator));
                                        });
                            });
                default:
                    return Future.failedFuture("Can not determine type of response!");
            }
        } catch (RuntimeException e) {
            return Future.failedFuture(e);
        }
    }

    private Future<Authenticator> verifyWebAuthnCreate(JsonObject response, WebAuthn4JCredentials authInfo, byte[] clientDataJSON) {
        byte[] attestationObject = base64UrlDecode(response.getString("attestationObject"));
        RegistrationRequest registrationRequest = new RegistrationRequest(attestationObject, clientDataJSON, null, null);

        List<PublicKeyCredentialParameters> pubKeyCredParams = new ArrayList<>(options.getPubKeyCredParams().size());
        for (COSEAlgorithm algorithm : options.getPubKeyCredParams()) {
            pubKeyCredParams.add(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.create(algorithm.coseId())));
        }
        // user verification is checked below with the rule of vertx-auth-webauthn
        RegistrationParameters registrationParameters = new RegistrationParameters(getServerProperty(authInfo), pubKeyCredParams, false, false);

        return verified(webAuthnManager.verify(registrationRequest, registrationParameters))
                .map(registrationData -> {
                    AuthenticatorData<?> authenticatorData = registrationData.getAttestationObject().getAuthenticatorData();
                    if (options.getUserVerification() == UserVerification.REQUIRED && !authenticatorData.isFlagUV() && !authenticatorData.isFlagUP()) {
                        throw new UserNotVerifiedException("User was either not verified or present during credentials creation");
                    }
                    AttestationStatement attestationStatement = registrationData.getAttestationObject().getAttestationStatement();
                    AttestedCredentialData attestedCredentialData = authenticatorData.getAttestedCredentialData();
                    return new Authenticator()
                            .setFmt(attestationStatement.getFormat())
                            .setAaguid(attestedCredentialData.getAaguid().toString())
                            .setPublicKey(base64UrlEncode(objectConverter.getCborConverter().writeValueAsBytes(attestedCredentialData.getCOSEKey())))
                            .setCounter(authenticatorData.getSignCount())
                            .setCredID(base64UrlEncode(attestedCredentialData.getCredentialId()))
                            .setAttestationCertificates(convertAttestationCertificates(attestationStatement))
                            .setFlags(authenticatorData.getFlags());
                });
    }

    private Future<Long> verifyWebAuthnGet(JsonObject response, WebAuthn4JCredentials authInfo, byte[] clientDataJSON, Authenticator authenticator) {
        byte[] credentialId = base64UrlDecode(authInfo.getWebauthn().getString("id"));
        byte[] userHandle = response.containsKey("userHandle") ? base64UrlDecode(response.getString("userHandle")) : null;
        byte[] authenticatorData = base64UrlDecode(response.getString("authenticatorData"));
        byte[] signature = base64UrlDecode(response.getString("signature"));
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(credentialId, userHandle, authenticatorData, clientDataJSON, null, signature);

        boolean userVerificationRequired = options.getUserVerification() == UserVerification.REQUIRED;
        AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                getServerProperty(authInfo),
                loadCredentialRecord(authenticator, presentedSignCount(authenticatorData)),
                List.of(base64UrlDecode(authenticator.getCredID())),
                userVerificationRequired,
                userVerificationRequired);

        return verified(webAuthnManager.verify(authenticationRequest, authenticationParameters))
                .map(authenticationData -> authenticationData.getAuthenticatorData().getSignCount());
    }

    /**
     * The result of a webauthn4j verification. A verification failure is given as is, not wrapped in the
     * {@link CompletionException} of the asynchronous stages, like vertx-auth-webauthn failed with its own
     * exception: the handlers and the error page rely on it.
     */
    private static <T> Future<T> verified(CompletionStage<T> verification) {
        return Future.fromCompletionStage(verification)
                .recover(error -> Future.failedFuture(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error));
    }

    private ServerProperty getServerProperty(WebAuthn4JCredentials authInfo) {
        Origin origin = Origin.create(authInfo.getOrigin());
        String rpId = authInfo.getDomain() != null ? authInfo.getDomain() : options.getRelyingParty().getId();
        if (rpId == null) {
            rpId = origin.getHost();
        }
        return new ServerProperty(origin, rpId, new DefaultChallenge(authInfo.getChallenge()), null);
    }

    /**
     * The stored credential as webauthn4j verifies it. An authenticator presenting a counter of 0 does not
     * implement it: the stored counter is then given as 0 too, so the assertion is accepted like
     * vertx-auth-webauthn did, whatever counter was stored before.
     */
    private CredentialRecord loadCredentialRecord(Authenticator authenticator, long presentedSignCount) {
        long counter = presentedSignCount == 0 ? 0 : authenticator.getCounter();
        COSEKey coseKey = objectConverter.getCborConverter().readValue(base64UrlDecode(authenticator.getPublicKey()), COSEKey.class);
        AttestedCredentialData attestedCredentialData = new AttestedCredentialData(
                new AAGUID(authenticator.getAaguid()), base64UrlDecode(authenticator.getCredID()), coseKey);
        return new CredentialRecordImpl(null, null, null, null, counter, attestedCredentialData, null, null, null, null);
    }

    /**
     * The signature counter of an authenticator data: bytes 33 to 36, after the rpIdHash and the flags.
     */
    private static long presentedSignCount(byte[] authenticatorData) {
        if (authenticatorData.length < 37) {
            return -1;
        }
        return ((authenticatorData[33] & 0xFFL) << 24) | ((authenticatorData[34] & 0xFFL) << 16)
                | ((authenticatorData[35] & 0xFFL) << 8) | (authenticatorData[36] & 0xFFL);
    }

    private static User toUser(Authenticator authenticator) {
        User user = User.create(authenticator.toJson());
        if ((authenticator.getFlags() & FLAG_USER_PRESENT) != 0) {
            user.principal().put("amr", Arrays.asList("user", "swk"));
        } else {
            user.principal().put("amr", Collections.singletonList("swk"));
        }
        return user;
    }

    private AttestationCertificates convertAttestationCertificates(AttestationStatement attestationStatement) {
        AttestationCertificates attestationCertificates = new AttestationCertificates();
        if (attestationStatement instanceof CertificateBaseAttestationStatement certificateBaseAttestationStatement) {
            AttestationCertificatePath x5c = certificateBaseAttestationStatement.getX5c();
            if (x5c != null) {
                attestationCertificates.setX5c(x5c.stream().map(certificate -> {
                    try {
                        return base64UrlEncode(certificate.getEncoded());
                    } catch (CertificateEncodingException e) {
                        throw new WebAuthn4JException(e);
                    }
                }).collect(Collectors.toList()));
            }
            if (attestationStatement instanceof AndroidKeyAttestationStatement androidKey) {
                attestationCertificates.setAlg(COSEAlgorithm.valueOf((int) androidKey.getAlg().getValue()));
            } else if (attestationStatement instanceof AndroidSafetyNetAttestationStatement safetyNet) {
                attestationCertificates.setAlg(COSEAlgorithm.valueOf(safetyNet.getResponse().getHeader().getAlg().getName()));
            } else if (attestationStatement instanceof FIDOU2FAttestationStatement) {
                attestationCertificates.setAlg(COSEAlgorithm.valueOf((int) COSEAlgorithmIdentifier.ES256.getValue()));
            } else if (attestationStatement instanceof PackedAttestationStatement packed) {
                attestationCertificates.setAlg(COSEAlgorithm.valueOf((int) packed.getAlg().getValue()));
            } else if (attestationStatement instanceof TPMAttestationStatement tpm) {
                attestationCertificates.setAlg(COSEAlgorithm.valueOf((int) tpm.getAlg().getValue()));
            }
        }
        return attestationCertificates;
    }

    private JsonArray transports() {
        JsonArray transports = new JsonArray();
        if (options.getTransports() != null) {
            for (AuthenticatorTransport transport : options.getTransports()) {
                transports.add(transport.toString());
            }
        }
        return transports;
    }

    private String randomBase64URLBuffer(int length) {
        final byte[] buff = new byte[length];
        random.nextBytes(buff);
        return base64UrlEncode(buff);
    }

    private static void putOpt(JsonObject json, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Enum<?>) {
            json.put(key, value.toString());
        } else if (!(value instanceof JsonObject object && object.isEmpty()) && !(value instanceof JsonArray array && array.isEmpty())) {
            json.put(key, value);
        }
    }

    private static void addOpt(JsonArray json, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Enum<?>) {
            json.add(value.toString());
        } else if (!(value instanceof JsonObject object && object.isEmpty()) && !(value instanceof JsonArray array && array.isEmpty())) {
            json.add(value);
        }
    }

    private static String uuidToBase64Url(UUID uuid) {
        Buffer buffer = Buffer.buffer(16);
        buffer.setLong(0, uuid.getMostSignificantBits());
        buffer.setLong(8, uuid.getLeastSignificantBits());
        return base64UrlEncode(buffer.getBytes());
    }
}
