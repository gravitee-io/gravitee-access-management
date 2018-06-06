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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint;

import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * The UserInfo Endpoint is an OAuth 2.0 Protected Resource that returns Claims about the authenticated End-User.
 * To obtain the requested Claims about the End-User, the Client makes a request to the UserInfo Endpoint using an Access Token obtained through OpenID Connect Authentication.
 * These Claims are normally represented by a JSON object that contains a collection of name and value pairs for the Claims.
 *
 * See <a href="http://openid.net/specs/openid-connect-core-1_0.html#UserInfo">5.3. UserInfo Endpoint</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserInfoEndpoint implements Handler<RoutingContext> {

    private UserService userService;

    public UserInfoEndpoint() {
    }

    public UserInfoEndpoint(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        DefaultAccessToken accessToken = context.get(AccessToken.ACCESS_TOKEN);

        // The UserInfo Endpoint is an OAuth 2.0 Protected Resource that returns Claims about the authenticated End-User
        String subject = accessToken.getSubject();
        if (subject == null) {
            // TODO : what error should be thrown ? rfc seems to say nothing about an access token created from the client_credentials flow
            context.fail(400);
            return;
        }

        // TODO : 5.4. Requesting Claims using Scope Values (http://openid.net/specs/openid-connect-core-1_0.html#UserInfo)
        // TODO : 5.1 Standard Claims (http://openid.net/specs/openid-connect-core-1_0.html#StandardClaims)
        userService.findById(subject)
                .subscribe(user -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(Json.encodePrettily(user.getAdditionalInformation()))
                        , error -> context.fail(error));

    }
}
