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
package io.gravitee.am.gateway.services.sync.api;

import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;

/**
 * @author GraviteeSource Team
 */
@Slf4j
public class DomainReadinessHandler implements Handler<RoutingContext> {

    @Autowired
    private DomainReadinessService domainReadinessService;

    @Override
    public void handle(RoutingContext context) {
        String domainId = context.request().getParam("domainId");

        if (domainId == null) {
            context.fail(HttpStatusCode.BAD_REQUEST_400);
            return;
        }

        var details = domainReadinessService.getDomainState(domainId);
        if (details == null) {
            context.fail(HttpStatusCode.NOT_FOUND_404);
            return;
        }

        if (details.isSynchronized() && details.isStable()) {
            context.response()
                    .setStatusCode(HttpStatusCode.OK_200)
                    .end();
        } else {
            log.debug("Domain {} is not ready (stable: {}, synchronized: {})", domainId, details.isStable(),
                    details.isSynchronized());
            context.response()
                    .setStatusCode(HttpStatusCode.OK_200)
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end(Json.encode(details));
        }
    }
}
