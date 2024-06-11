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
package io.gravitee.sample.ciba.notifier.http.mock;

import com.nimbusds.jose.JOSEObject;
import io.gravitee.sample.ciba.notifier.http.domain.CibaDomainManager;
import io.gravitee.sample.ciba.notifier.http.model.DomainReference;
import io.gravitee.sample.ciba.notifier.http.model.NotifierRequest;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import net.minidev.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static io.gravitee.sample.ciba.notifier.http.Constants.CALLBACK_VALIDATE;
import static io.gravitee.sample.ciba.notifier.http.Constants.STATE;
import static io.gravitee.sample.ciba.notifier.http.Constants.TOPIC_NOTIFICATION_REQUEST;
import static io.gravitee.sample.ciba.notifier.http.Constants.TRANSACTION_ID;
/**
 * Actionize handler used to accept or reject the next NotificationRequest according to the
 * action parameter (allow or deny)
 *
 * This handler has been introduced to manage the "Automated Testing" for FAPI-CIBA conformance.
 * https://openid.net/certification/fapi_ciba_op_testing/
 *
 */
public class CibaAutomatedTestActionApiHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CibaAutomatedTestActionApiHandler.class);
    public static final String ACTION = "action";
    public static final String ACTION_ALLOW = "allow";
    public static final String ACTION_DISPLAY = "display";

    private final CibaDomainManager domainManager;
    private final WebClient webClient;
    private final EventBus eventBus;

    private final AtomicReference<NotifierRequest> queue = new AtomicReference<>();

    public CibaAutomatedTestActionApiHandler(CibaDomainManager domainManager, Vertx vertx, WebClientOptions options) {
        this.domainManager = domainManager;
        this.eventBus = vertx.eventBus();
        this.eventBus.consumer(TOPIC_NOTIFICATION_REQUEST, (msg) -> {
            final String json = msg.body().toString();
            final NotifierRequest notifierRequest = Json.decodeValue(json, NotifierRequest.class);
            this.queue.set(notifierRequest);
        });

        this.webClient = WebClient.create(vertx, options);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            final String action = routingContext.request().getParam(ACTION);
            LOGGER.info("Actionize received with action '{}'", action);

            final NotifierRequest notificationRequest = queue.get();
            if (Objects.isNull(notificationRequest)) {
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(404)
                        .end("missing notification request");
                return;
            }

            LOGGER.info("Actionize received with action '{}' for request {}", action, Json.encode(notificationRequest) );

            if (ACTION_DISPLAY.equalsIgnoreCase(action)) {
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(200)
                        .end(Json.encode(notificationRequest));
            }

            final String state = notificationRequest.getState();
            final JOSEObject parsedJWT = JOSEObject.parse(state);
            final String domainId = new JSONObject(parsedJWT.getPayload().toJSONObject()).getAsString("iss");

            final Optional<DomainReference> optCallback = this.domainManager.getDomainRef(domainId);
            if (optCallback.isPresent()) {
                final MultiMap formData = MultiMap.caseInsensitiveMultiMap();

                formData.set(TRANSACTION_ID, notificationRequest.getTid());
                formData.set(STATE, state);
                formData.set(CALLBACK_VALIDATE, Boolean.toString(ACTION_ALLOW.equalsIgnoreCase(action)));

                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(200)
                        .end();

                sendResponse(notificationRequest.getTid(), optCallback.get(), formData, true);

            } else {
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(404)
                        .end("missing domain reference");
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to manage the notification request", e);
            routingContext.fail(500, e);
        }
    }

    private void sendResponse(String transactionId, DomainReference optCallback, MultiMap formData, boolean retry) {
        Executors.defaultThreadFactory().newThread(() -> {
            try {
                // give to the AM GW enough time to update the Request external ID
                waitBeforeNotification();
                LOGGER.info("Callback {} for tid {} with form params {}", optCallback.getDomainCallback(), transactionId, formData);
                webClient
                        .postAbs(optCallback.getDomainCallback())
                        .authentication(new UsernamePasswordCredentials(
                                optCallback.getClientId(),
                                optCallback.getClientSecret()))
                        .sendForm(formData)
                        .onSuccess(res -> {
                            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                                LOGGER.info("Callback succeeded for tid {}", transactionId);
                            } else {
                                LOGGER.error("Callback failed for tid {}. Status Code = {}", transactionId, res.statusCode());
                            }
                        })
                        .onFailure(err -> {
                            if (retry) {
                                LOGGER.info("Retry the callback for tid {} (err: {})", transactionId, err);
                                sendResponse(transactionId, optCallback, formData, false);
                            } else {
                                LOGGER.warn("Callback failed for tid {} : {}", transactionId, err);
                            }
                        });
            } catch (Exception e) {
                LOGGER.warn("Callback request failed for tid {} : {}", transactionId, e);
            }
        }).start();
    }

    private void waitBeforeNotification() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) { /*Silent catch*/ }
    }
}
