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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.MultiMap;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CibaClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CibaClient.class);

    private static final String CIBA_GRANT = "urn:openid:params:grant-type:ciba";

    public enum PollKind { PENDING, SLOW_DOWN, TOKEN, ERROR }

    public record BcAuthorizeResult(String authReqId, int expiresInSeconds, int intervalSeconds) {}
    public record PollResult(PollKind kind, String accessToken, String idToken, Object authorizationDetails, String error) {}

    private final WebClient client;
    private final ProviderMetadata metadata;
    private final String clientId;
    private final String clientSecret;
    private final String audience;
    private final String clientAuthMethod;

    public CibaClient(WebClient client, ProviderMetadata metadata,
                      String clientId, String clientSecret, String audience, String clientAuthMethod) {
        if (!ClientAuthentication.isSupported(clientAuthMethod)) {
            throw new IllegalStateException(ClientAuthentication.unsupportedMessage(clientAuthMethod));
        }
        this.client = client;
        this.metadata = metadata;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.clientAuthMethod = clientAuthMethod;
    }

    public Single<BcAuthorizeResult> bcAuthorize(CibaHints hints, String scope, String bindingMessage, Object relayedAuthorizationDetails) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap()
                .set("client_id", clientId)
                .set("scope", scope);
        if (audience != null && !audience.isBlank()) form.set("audience", audience);
        // Relay the (already-decorated) hint(s) as received. Exactly one is present (enforced in the
        // provider); forward whichever, incl. id_token_hint, so a strategy's output is never dropped.
        if (hints.loginHint() != null && !hints.loginHint().isBlank()) form.set("login_hint", hints.loginHint());
        if (hints.loginHintToken() != null && !hints.loginHintToken().isBlank()) form.set("login_hint_token", hints.loginHintToken());
        if (hints.idTokenHint() != null && !hints.idTokenHint().isBlank()) form.set("id_token_hint", hints.idTokenHint());
        if (bindingMessage != null && !bindingMessage.isBlank()) form.set("binding_message", bindingMessage);
        if (relayedAuthorizationDetails != null) form.set("authorization_details", Json.encode(relayedAuthorizationDetails));

        return ClientAuthentication.applied(client.postAbs(metadata.backchannelAuthEndpoint()), form, clientAuthMethod, clientId, clientSecret)
                .rxSendForm(form).map(resp -> {
            if (resp.statusCode() != 200) {
                JsonObject errBody = resp.bodyAsJsonObject();
                String err = errBody != null ? errBody.getString("error", String.valueOf(resp.statusCode()))
                                             : String.valueOf(resp.statusCode());
                LOGGER.warn("CIBA-FED bc-authorize failed: status={} error={}", resp.statusCode(), err);
                throw new IllegalStateException("CIBA federation: back-channel authorization request was rejected by the identity provider");
            }
            JsonObject body = resp.bodyAsJsonObject();
            if (body == null) throw new IllegalStateException("CIBA federation: back-channel authorization returned an empty response");
            String authReqId = body.getString("auth_req_id");
            if (authReqId == null || authReqId.isBlank()) {
                throw new IllegalStateException("CIBA federation: back-channel authorization response omitted auth_req_id");
            }
            return new BcAuthorizeResult(authReqId, body.getInteger("expires_in", 120), body.getInteger("interval", 5));
        });
    }

    public Single<PollResult> pollToken(String authReqId) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap()
                .set("client_id", clientId)
                .set("auth_req_id", authReqId).set("grant_type", CIBA_GRANT);
        return ClientAuthentication.applied(client.postAbs(metadata.tokenEndpoint()), form, clientAuthMethod, clientId, clientSecret)
                .rxSendForm(form).map(resp -> {
            JsonObject body = resp.bodyAsJsonObject();
            if (resp.statusCode() == 200) {
                if (body == null) throw new IllegalStateException("pollToken returned empty body on 200");
                return new PollResult(PollKind.TOKEN, body.getString("access_token"), body.getString("id_token"),
                        body.getValue("authorization_details") instanceof io.vertx.core.json.JsonArray ja ? ja.getList() : null, null);
            }
            String err = body != null ? body.getString("error", String.valueOf(resp.statusCode()))
                                      : String.valueOf(resp.statusCode());
            if ("authorization_pending".equals(err)) return new PollResult(PollKind.PENDING, null, null, null, err);
            if ("slow_down".equals(err)) return new PollResult(PollKind.SLOW_DOWN, null, null, null, err);
            return new PollResult(PollKind.ERROR, null, null, null, err);
        });
    }
}
