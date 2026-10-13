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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity;

import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttachment;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.AuthenticatorAdaptor;
import com.webauthn4j.test.authenticator.u2f.FIDOU2FAuthenticatorAdaptor;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import com.webauthn4j.test.client.RegistrationEmulationOption;
import com.webauthn4j.util.Base64UrlUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A browser driving one emulated authenticator: it takes the options JSON the relying party hands to
 * {@code navigator.credentials.create/get} and returns the JSON the AM login/register pages post back.
 *
 * @author GraviteeSource Team
 */
final class EmulatedBrowser {

    private final ClientPlatform clientPlatform;
    private UserVerificationRequirement userVerificationOverride;

    private EmulatedBrowser(String origin, AuthenticatorAdaptor authenticatorAdaptor) {
        this.clientPlatform = new ClientPlatform(new Origin(origin), authenticatorAdaptor);
    }

    static EmulatedBrowser of(String origin, WebAuthnAuthenticator authenticator) {
        return new EmulatedBrowser(origin, new WebAuthnAuthenticatorAdaptor(authenticator));
    }

    static EmulatedBrowser ofU2f(String origin) {
        return new EmulatedBrowser(origin, new FIDOU2FAuthenticatorAdaptor());
    }

    /**
     * Ask the authenticator for this user verification requirement whatever the relying party asked for,
     * the way a client may downgrade it.
     */
    EmulatedBrowser userVerification(UserVerificationRequirement userVerification) {
        this.userVerificationOverride = userVerification;
        return this;
    }

    EmulatedBrowser origin(String origin) {
        clientPlatform.setOrigin(new Origin(origin));
        return this;
    }

    JsonObject create(JsonObject options) {
        JsonObject rp = options.getJsonObject("rp");
        JsonObject user = options.getJsonObject("user");
        JsonObject selection = options.getJsonObject("authenticatorSelection", new JsonObject());

        List<PublicKeyCredentialParameters> pubKeyCredParams = new ArrayList<>();
        for (Object param : options.getJsonArray("pubKeyCredParams", new JsonArray())) {
            pubKeyCredParams.add(new PublicKeyCredentialParameters(
                    PublicKeyCredentialType.PUBLIC_KEY,
                    COSEAlgorithmIdentifier.create(((JsonObject) param).getLong("alg"))));
        }

        PublicKeyCredentialCreationOptions creationOptions = new PublicKeyCredentialCreationOptions(
                new PublicKeyCredentialRpEntity(rp.getString("id"), rp.getString("name")),
                new PublicKeyCredentialUserEntity(
                        userHandle(user.getString("id")),
                        user.getString("name"),
                        user.getString("displayName", user.getString("name"))),
                new DefaultChallenge(options.getString("challenge")),
                pubKeyCredParams,
                options.getLong("timeout"),
                descriptors(options.getJsonArray("excludeCredentials")),
                new AuthenticatorSelectionCriteria(
                        selection.getString("authenticatorAttachment") == null ? null : AuthenticatorAttachment.create(selection.getString("authenticatorAttachment")),
                        selection.getBoolean("requireResidentKey", false),
                        selection.getString("residentKey") == null ? null : ResidentKeyRequirement.create(selection.getString("residentKey")),
                        userVerification(selection.getString("userVerification"))),
                // the emulator implements NONE and DIRECT only, INDIRECT/ENTERPRISE behave as DIRECT here
                "none".equals(options.getString("attestation", "none")) ? AttestationConveyancePreference.NONE : AttestationConveyancePreference.DIRECT,
                null);

        PublicKeyCredential<AuthenticatorAttestationResponse, ?> credential =
                clientPlatform.create(creationOptions, new RegistrationEmulationOption());
        return new JsonObject()
                .put("id", Base64UrlUtil.encodeToString(credential.getRawId()))
                .put("rawId", Base64UrlUtil.encodeToString(credential.getRawId()))
                .put("type", "public-key")
                .put("response", new JsonObject()
                        .put("clientDataJSON", Base64UrlUtil.encodeToString(credential.getResponse().getClientDataJSON()))
                        .put("attestationObject", Base64UrlUtil.encodeToString(credential.getResponse().getAttestationObject())));
    }

    JsonObject get(JsonObject options) {
        PublicKeyCredentialRequestOptions requestOptions = new PublicKeyCredentialRequestOptions(
                new DefaultChallenge(options.getString("challenge")),
                options.getLong("timeout"),
                options.getString("rpId"),
                descriptors(options.getJsonArray("allowCredentials")),
                userVerification(options.getString("userVerification")),
                null,
                null);

        PublicKeyCredential<AuthenticatorAssertionResponse, ?> credential = clientPlatform.get(requestOptions);
        JsonObject response = new JsonObject()
                .put("clientDataJSON", Base64UrlUtil.encodeToString(credential.getResponse().getClientDataJSON()))
                .put("authenticatorData", Base64UrlUtil.encodeToString(credential.getResponse().getAuthenticatorData()))
                .put("signature", Base64UrlUtil.encodeToString(credential.getResponse().getSignature()));
        if (credential.getResponse().getUserHandle() != null) {
            response.put("userHandle", Base64UrlUtil.encodeToString(credential.getResponse().getUserHandle()));
        }
        return new JsonObject()
                .put("id", Base64UrlUtil.encodeToString(credential.getRawId()))
                .put("rawId", Base64UrlUtil.encodeToString(credential.getRawId()))
                .put("type", "public-key")
                .put("response", response);
    }

    private UserVerificationRequirement userVerification(String requested) {
        if (userVerificationOverride != null) {
            return userVerificationOverride;
        }
        return requested == null ? UserVerificationRequirement.PREFERRED : UserVerificationRequirement.create(requested);
    }

    private static List<PublicKeyCredentialDescriptor> descriptors(JsonArray json) {
        List<PublicKeyCredentialDescriptor> descriptors = new ArrayList<>();
        if (json != null) {
            for (Object descriptor : json) {
                descriptors.add(new PublicKeyCredentialDescriptor(
                        PublicKeyCredentialType.PUBLIC_KEY,
                        Base64UrlUtil.decode(((JsonObject) descriptor).getString("id")),
                        null));
            }
        }
        return descriptors;
    }

    private static byte[] userHandle(String userId) {
        try {
            return Base64UrlUtil.decode(userId);
        } catch (IllegalArgumentException e) {
            return userId.getBytes(StandardCharsets.UTF_8);
        }
    }
}
