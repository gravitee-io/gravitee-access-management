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

import com.fasterxml.jackson.databind.deser.impl.InnerClassProperty;
import com.nimbusds.jose.JOSEObject;
import io.gravitee.sample.ciba.notifier.http.domain.CibaDomainManager;
import io.gravitee.sample.ciba.notifier.CibaHttpNotifier;
import io.gravitee.sample.ciba.notifier.http.model.DomainReference;
import io.gravitee.sample.ciba.notifier.http.model.NotifierResponse;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executors;

import static io.gravitee.sample.ciba.notifier.http.Constants.*;

public class CibaMockNotifierApiHandler implements Handler<RoutingContext> {
    private static Logger LOGGER = LoggerFactory.getLogger(CibaMockNotifierApiHandler.class);

    private final boolean accept;
    private final String authBearer;
    private CibaDomainManager domainManager;
    private WebClient webClient;

    public CibaMockNotifierApiHandler(boolean accept, CommandLine parameters, CibaDomainManager domainManager, Vertx vertx) {
        this.accept = accept;
        this.domainManager = domainManager;
        this.authBearer = parameters.getOptionValue(CibaHttpNotifier.CONF_BEARER);

        WebClientOptions options = new WebClientOptions().setUserAgent("AM CIBA Delegate HTTP Service");
        options.setKeepAlive(false);

        this.webClient = WebClient.create(vertx, options);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {

            final String auth = routingContext.request().getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith(BEARER) && !StringUtil.isNullOrEmpty(authBearer)) {
                // control the token
                if (!authBearer.equals(auth.substring(BEARER.length()))) {
                    routingContext.response()
                            .putHeader("content-type", "application/json")
                            .setStatusCode(401).end();
                    return ;
                }
            }

            final String transactionId = routingContext.request().getFormAttribute(TRANSACTION_ID);
            final String state = routingContext.request().getFormAttribute(STATE);
            final JOSEObject parsedJWT = JOSEObject.parse(state);
            final String domainId = parsedJWT.getPayload().toJSONObject().getAsString("iss");

            final Optional<DomainReference> optCallback = this.domainManager.getDomainRef(domainId);
            if (optCallback.isPresent()) {
                final MultiMap formData = MultiMap.caseInsensitiveMultiMap();

                formData.set(TRANSACTION_ID, transactionId);
                formData.set(STATE, state);
                formData.set(CALLBACK_VALIDATE, Boolean.toString(accept));

                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(200)
                        .end(Json.encode(new NotifierResponse(transactionId, state)));

                sendResponse(transactionId, optCallback.get(), formData, true);

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
                webClient
                        .postAbs(optCallback.getDomainCallback())
                        .authentication(new UsernamePasswordCredentials(
                                optCallback.getClientId(),
                                optCallback.getClientSecret()))
                        .sendForm(formData)
                        .onSuccess(res -> LOGGER.info("Callback succeeded for tid {}", transactionId))
                        .onFailure(err -> {
                            if (retry) {
                                LOGGER.info("Retry the callback for tid {} (err: {})", transactionId, err);
                                // give to the AM GW enough time to update the Request external ID
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) { /*Silent catch*/ }
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
}
