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
package io.gravitee.am.gateway.handler.uma.resources.handler;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.gateway.handler.oauth2.exception.ResourceNotFoundException;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.service.response.OAuth2ErrorResponse;
import io.gravitee.am.service.exception.ResourceSetNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UmaExceptionHandler extends ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(UmaExceptionHandler.class);

    @Override
    public void handle(RoutingContext routingContext) {
        if(routingContext.failed()) {
            Throwable throwable = routingContext.failure();

            if (throwable instanceof ResourceSetNotFoundException) {
                OAuth2Exception oAuth2Exception = new ResourceNotFoundException(throwable.getMessage());
                OAuth2ErrorResponse oAuth2ErrorResponse = new OAuth2ErrorResponse(oAuth2Exception.getOAuth2ErrorCode());
                oAuth2ErrorResponse.setDescription(oAuth2Exception.getMessage());
                routingContext
                        .response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .setStatusCode(oAuth2Exception.getHttpStatusCode())
                        .end(Json.encodePrettily(oAuth2ErrorResponse));
            } else {
                super.handle(routingContext);
            }
        }
    }
}
