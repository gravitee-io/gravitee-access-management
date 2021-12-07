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
package io.gravitee.sample.ciba.notifier.http;

import com.nimbusds.jose.JOSEObject;
import io.gravitee.sample.ciba.notifier.http.domain.CibaDomainManager;
import io.gravitee.sample.ciba.notifier.http.model.DomainReference;
import io.gravitee.sample.ciba.notifier.http.model.NotifierRequest;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.gravitee.sample.ciba.notifier.http.Constants.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaNotifierWebSockerHandler implements Handler<ServerWebSocket> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CibaNotifierWebSockerHandler.class);

    private EventBus eventBus;
    private WebClient webClient;
    private CibaDomainManager domainManager;

    private Map<String, ServerWebSocket> serverWebSocket = new HashMap<>();

    public CibaNotifierWebSockerHandler(Vertx vertx, CibaDomainManager domainManager) {
        this.eventBus = vertx.eventBus();
        this.eventBus.consumer(TOPIC_NOTIFICATION_REQUEST, (msg) -> {
            final String json = msg.body().toString();
            final NotifierRequest notifierRequest = Json.decodeValue(json, NotifierRequest.class);
            if (this.serverWebSocket.containsKey(notifierRequest.getSubject())) {
                this.serverWebSocket.get(notifierRequest.getSubject()).writeTextMessage(json);
            }
        });

        WebClientOptions options = new WebClientOptions().setUserAgent("AM CIBA Delegate HTTP Service");
        options.setKeepAlive(false);

        this.webClient = WebClient.create(vertx, options);

        this.domainManager = domainManager;
    }

    @Override
    public void handle(ServerWebSocket ws) {
        ws.textMessageHandler(msg -> {
            LOGGER.debug("Received user message: " + msg);
            final JsonObject jsonMsg = (JsonObject)Json.decodeValue(msg);

            final String action = jsonMsg.getString(ACTION);

            if (Objects.equals(action, ACTION_SIGN_IN)) {

                final String subject = jsonMsg.getString(PARAM_SUBJECT);
                this.serverWebSocket.put(subject, ws);

                // clean the map on final event
                ws.endHandler((__) -> {
                    LOGGER.debug("Connection closed by subject {}", subject);
                    this.serverWebSocket.remove(subject);
                });

            } else if (Objects.equals(action, ACTION_VALIDATE) || Objects.equals(action, ACTION_REJECT)) {
                doCallback(jsonMsg);
            }
        });
    }

    private void doCallback(JsonObject jsonMsg) {
        final String action = jsonMsg.getString(ACTION);
        final String transactionId = jsonMsg.getString(TRANSACTION_ID);
        final String state = jsonMsg.getString(STATE);

        try {
            final JOSEObject parsedJWT = JOSEObject.parse(state);
            final String domainId = parsedJWT.getPayload().toJSONObject().getAsString("iss");

            final Optional<DomainReference> optCallback = this.domainManager.getDomainRef(domainId);
            if (optCallback.isPresent()) {
                final MultiMap formData = MultiMap.caseInsensitiveMultiMap();

                formData.set(TRANSACTION_ID, transactionId);
                formData.set(STATE, state);
                formData.set(CALLBACK_VALIDATE, Boolean.toString(Objects.equals(action, ACTION_VALIDATE)));

                this.webClient
                        .postAbs(optCallback.get().getDomainCallback())
                        .authentication(new UsernamePasswordCredentials(
                                optCallback.get().getClientId(),
                                optCallback.get().getClientSecret()))
                        .sendForm(formData)
                        .onSuccess(res -> LOGGER.info("Callback succeeded for tid {}", transactionId))
                        .onFailure(err -> LOGGER.warn("Callback failed for tid {} : {}", transactionId, err));
            } else {
                LOGGER.warn("Missing domain reference for domainId {}", domainId);
            }
        } catch (ParseException ex) {
            LOGGER.warn("Unable to parse the state {} for transactionId {}", state, transactionId, ex);
        }
    }
}
