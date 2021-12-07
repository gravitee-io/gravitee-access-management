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

import io.gravitee.sample.ciba.notifier.CibaHttpNotifier;
import io.gravitee.sample.ciba.notifier.http.model.NotifierRequest;
import io.gravitee.sample.ciba.notifier.http.model.NotifierResponse;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.sample.ciba.notifier.http.Constants.*;

public class CibaNotifierApiHandler implements Handler<RoutingContext> {
    private static Logger LOGGER = LoggerFactory.getLogger(CibaNotifierApiHandler.class);

    private EventBus eventBus;
    private String authBearer;

    public CibaNotifierApiHandler(CommandLine parameters, Vertx vertx) {
        this.eventBus = vertx.eventBus();
        this.authBearer = parameters.getOptionValue(CibaHttpNotifier.CONF_BEARER);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            final HttpServerRequest httpRequest = routingContext.request();
            final String auth = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith(BEARER) && !StringUtil.isNullOrEmpty(authBearer)) {
                // control the token
                if (!authBearer.equals(auth.substring(BEARER.length()))) {
                    routingContext.response()
                            .putHeader("content-type", "application/json")
                            .setStatusCode(401).end();
                    return ;
                }
            }

            NotifierRequest notifierRequest = new NotifierRequest();
            notifierRequest.setTid(httpRequest.getFormAttribute(TRANSACTION_ID));
            notifierRequest.setState(httpRequest.getFormAttribute(STATE));
            notifierRequest.setMessage(httpRequest.getFormAttribute(PARAM_MESSAGE));
            notifierRequest.setSubject(httpRequest.getFormAttribute(PARAM_SUBJECT));
            notifierRequest.setExpiresIn(Integer.parseInt(httpRequest.getFormAttribute(PARAM_EXPIRE)));
            notifierRequest.setScopes(httpRequest.formAttributes().getAll(PARAM_SCOPE));

            eventBus.publish(TOPIC_NOTIFICATION_REQUEST, Json.encode(notifierRequest));

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(Json.encode(new NotifierResponse(httpRequest.getFormAttribute(TRANSACTION_ID), httpRequest.getFormAttribute(STATE))));
        } catch (Exception e) {
            LOGGER.warn("Unable to manage the notification request", e);
            routingContext.fail(500, e);
        }
    }
}
