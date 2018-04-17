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
package io.gravitee.am.gateway.handler.vertx.handler;

import io.gravitee.am.gateway.handler.oauth2.exception.OAuth2Exception;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExceptionHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof OAuth2Exception) {
                OAuth2Exception oAuth2Exception = (OAuth2Exception) throwable;
                routingContext
                        .response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .setStatusCode(oAuth2Exception.getHttpStatusCode())
                        .end(new JsonObject(oAuth2Exception.toString()).encodePrettily());
            } else {
                if (routingContext.statusCode() != -1) {
                    routingContext
                            .response()
                            .setStatusCode(routingContext.statusCode())
                            .end();
                } else {
                    routingContext
                            .response()
                            .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                            .end();
                }
            }
        }
    }
}
