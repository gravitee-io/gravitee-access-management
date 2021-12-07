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
package io.gravitee.sample.ciba.notifier.http.domain;

import io.gravitee.sample.ciba.notifier.http.model.DomainReference;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CibaDomainManagerHandler implements Handler<RoutingContext> {
    private static Logger LOGGER = LoggerFactory.getLogger(CibaDomainManagerHandler.class);

    private CibaDomainManager manager;

    public CibaDomainManagerHandler(CibaDomainManager manager) {
        this.manager = manager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            routingContext
                    .request()
                    .body()
                    .map(b -> Json.decodeValue(b, DomainReference.class))
                    .onSuccess(domainRef -> {
                        this.manager.registerDomain(domainRef);
                        routingContext.response().setStatusCode(200).end();
                    })
                    .onFailure(err -> {
                        LOGGER.warn("Unable to register the domain reference", err);
                        routingContext.response().setStatusCode(500).end();
                    });
        } catch (Exception e) {
            LOGGER.warn("Unable to register the domain", e);
            routingContext.fail(500, e);
        }

    }
}
