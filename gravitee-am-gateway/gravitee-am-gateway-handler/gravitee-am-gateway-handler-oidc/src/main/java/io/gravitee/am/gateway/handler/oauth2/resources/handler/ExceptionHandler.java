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
package io.gravitee.am.gateway.handler.oauth2.resources.handler;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.exception.uma.UmaException;
import io.gravitee.am.gateway.handler.oauth2.service.response.OAuth2ErrorResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.UMAErrorResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.UMARequiredClaimsError;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExceptionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof OAuth2Exception) {
                OAuth2Exception oAuth2Exception = (OAuth2Exception) throwable;
                OAuth2ErrorResponse oAuth2ErrorResponse = new OAuth2ErrorResponse(oAuth2Exception.getOAuth2ErrorCode());
                oAuth2ErrorResponse.setDescription(oAuth2Exception.getMessage());
                routingContext
                        .response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .setStatusCode(oAuth2Exception.getHttpStatusCode())
                        .end(Json.encodePrettily(oAuth2ErrorResponse));
            } else if (throwable instanceof UmaException) {
                UmaException umaException = (UmaException) throwable;
                UMAErrorResponse umaErrorResponse = new UMAErrorResponse(umaException.getError())
                        .setTicket(umaException.getTicket())
                        .setRedirectUser(umaException.getRedirectUser())
                        .setInterval(umaException.getInterval())
                        .setRequiredClaims(this.from(umaException));
                routingContext
                        .response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .setStatusCode(umaException.getStatus())
                        .end(Json.encodePrettily(umaErrorResponse));
            } else if (throwable instanceof PolicyChainException) {
                PolicyChainException policyChainException = (PolicyChainException) throwable;
                OAuth2ErrorResponse oAuth2ErrorResponse = new OAuth2ErrorResponse(policyChainException.key());
                oAuth2ErrorResponse.setDescription(policyChainException.getMessage());
                routingContext
                        .response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .setStatusCode(policyChainException.statusCode())
                        .end(Json.encodePrettily(oAuth2ErrorResponse));
            } else if (throwable instanceof HttpException) {
                routingContext
                        .response()
                        .setStatusCode(((HttpException) throwable).getStatusCode())
                        .end();
            } else {
                logger.error("An exception occurs while handling incoming request", throwable);
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

    private List<UMARequiredClaimsError> from(UmaException umaException) {
        if(umaException.getRequiredClaims()!=null) {
            return umaException.getRequiredClaims().stream().map(UMARequiredClaimsError::from).collect(Collectors.toList());
        }
        return null;
    }
}
