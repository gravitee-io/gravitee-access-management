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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.gateway.handler.oidc.model.jwk.converter.JWKConverter;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * The JWKSet Endpoint provide a set of JWKs (keys) to enable clients to verify the authenticity of JWT tokens.
 *
 * A JWK Set is a JSON object that represents a set of JWKs.
 * The JSON object MUST have a "keys" member, with its value being an array ofxJWKs.
 * This JSON object MAY contain whitespace and/or line breaks.
 *
 * See <a href="https://tools.ietf.org/html/rfc7517#section-5">5. JWK Set Format</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ProviderJWKSetEndpoint implements Handler<RoutingContext> {

    private JWKService jwkService;

    public ProviderJWKSetEndpoint(JWKService jwkService) {
        this.jwkService = jwkService;
    }

    @Override
    public void handle(RoutingContext context) {
        jwkService
            .getKeys()
            .map(JWKConverter::convert)
            .subscribe(
                keys ->
                    context
                        .response()
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/jwk-set+json; charset=UTF-8")
                        .end(Json.encodePrettily(keys)),
                error -> context.response().setStatusCode(500).end()
            );
    }
}
