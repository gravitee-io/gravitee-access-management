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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.handler;

import com.google.common.base.Strings;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.token.impl.AccessToken;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Maybe;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public abstract class AbstractProtectedHandler implements Handler<RoutingContext> {

    protected Maybe<String> extractAccessTokenFromRequest(RoutingContext context) {
        //Extract first from Authorization Header
        final String authorization = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        if(authorization!=null) {
            if(!authorization.trim().startsWith(AccessToken.BEARER_TYPE+" ")) {
                return Maybe.error(new InvalidRequestException("The access token must be sent using the Authorization header with as value \"Bearer xxxx\""));
            }

            return Maybe.just(authorization.replaceFirst(AccessToken.BEARER_TYPE+" ",""));
        }

        //Extract from query parameter.
        final String accessToken = context.request().getParam(AccessToken.ACCESS_TOKEN);
        if(!Strings.isNullOrEmpty(accessToken)) {
            return Maybe.just(accessToken);
        }

        return Maybe.error(new InvalidRequestException("An access token is required"));
    }
}
