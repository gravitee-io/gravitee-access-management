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
package io.gravitee.am.gateway.handler.scim.resources;

import io.gravitee.am.gateway.handler.scim.model.Error;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The SCIM protocol uses the HTTP response status codes defined in
 *    Section 6 of [RFC7231] to indicate operation success or failure.  In
 *    addition to returning an HTTP response code, implementers MUST return
 *    the errors in the body of the response in a JSON format, using the
 *    attributes described below.  Error responses are identified using the
 *    following "schema" URI:
 *    "urn:ietf:params:scim:api:messages:2.0:Error".  The following
 *    attributes are defined for a SCIM error response using a JSON body:
 *
 *    status
 *       The HTTP status code (see Section 6 of [RFC7231]) expressed as a
 *       JSON string.  REQUIRED.
 *
 *    scimType
 *       A SCIM detail error keyword.  See Table 9.  OPTIONAL.
 *
 *    detail
 *       A detailed human-readable message.  OPTIONAL.
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.12">3.12. HTTP Status and Error Response Handling</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ErrorHandler implements Handler<RoutingContext> {

    private static Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            final Throwable throwable = routingContext.failure();
            Optional<Error> knownError = Error.fromThrowable(throwable);
            knownError
                    .ifPresentOrElse(error -> handleScimError(routingContext, error),
                            () -> handleUnknownError(routingContext, throwable));

        }
    }

    private Completable handleScimError(RoutingContext routingContext, Error error) {
        return routingContext
                .response()
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                .setStatusCode(Integer.valueOf(error.getStatus()))
                .end(Json.encodePrettily(error));
    }

    private void handleUnknownError(RoutingContext routingContext, Throwable throwable) {
        logger.error(throwable.getMessage(), throwable);
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
