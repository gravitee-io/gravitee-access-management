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
package io.gravitee.am.gateway.handler.vertx.handler.users.endpoint.consents;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Single;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.net.SocketAddress;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AbstractUserConsentEndpointHandler {

    protected UserService userService;
    private ClientSyncService clientSyncService;
    private Domain domain;

    public AbstractUserConsentEndpointHandler(UserService userService, ClientSyncService clientSyncService, Domain domain) {
        this.userService = userService;
        this.clientSyncService = clientSyncService;
        this.domain = domain;
    }

    protected Single<User> getPrincipal(RoutingContext context) {
        Token token = context.get(AccessToken.ACCESS_TOKEN);

        if (token.getSubject() == null) {
            return Single.just(defaultPrincipal(context, token));
        }

        // end user
        if (!token.getSubject().equals(token.getClientId())) {
            return userService.findById(token.getSubject())
                    .map(user -> {
                        User principal = new DefaultUser(user.getUsername());
                        ((DefaultUser) principal).setId(user.getId());
                        Map<String, Object> additionalInformation = new HashMap<>(user.getAdditionalInformation());
                        // add ip address and user agent
                        additionalInformation.put(Claims.ip_address, remoteAddress(context.request()));
                        additionalInformation.put(Claims.user_agent, userAgent(context.request()));
                        additionalInformation.put(Claims.domain, domain.getId());
                        ((DefaultUser) principal).setAdditionalInformation(additionalInformation);
                        return principal;
                    })
                    .defaultIfEmpty(defaultPrincipal(context, token))
                    .toSingle();
        } else {
            // revocation made oauth2 clients
            return clientSyncService.findByClientId(token.getClientId())
                    .map(client -> {
                        User principal = new DefaultUser(client.getClientId());
                        ((DefaultUser) principal).setId(client.getId());
                        Map<String, Object> additionalInformation = new HashMap<>();
                        // add ip address and user agent
                        additionalInformation.put(Claims.ip_address, remoteAddress(context.request()));
                        additionalInformation.put(Claims.user_agent, userAgent(context.request()));
                        additionalInformation.put(Claims.domain, domain.getId());
                        ((DefaultUser) principal).setAdditionalInformation(additionalInformation);
                        return principal;
                    })
                    .defaultIfEmpty(defaultPrincipal(context, token))
                    .toSingle();
        }

    }

    private User defaultPrincipal(RoutingContext context, Token token) {
        final String username = token.getSubject() != null ? token.getSubject() : "unknown-user";
        User principal = new DefaultUser(username);
        ((DefaultUser) principal).setId(username);
        Map<String, Object> additionalInformation = new HashMap<>();
        // add ip address and user agent
        additionalInformation.put(Claims.ip_address, remoteAddress(context.request()));
        additionalInformation.put(Claims.user_agent, userAgent(context.request()));
        additionalInformation.put(Claims.domain, domain.getId());
        ((DefaultUser) principal).setAdditionalInformation(additionalInformation);
        return principal;
    }

    private String remoteAddress(HttpServerRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if(xForwardedFor != null && xForwardedFor.length() > 0) {
            int idx = xForwardedFor.indexOf(',');

            remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;

            idx = remoteAddress.indexOf(':');

            remoteAddress = (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        } else {
            SocketAddress address = httpServerRequest.remoteAddress();
            remoteAddress = (address != null) ? address.host() : null;
        }

        return remoteAddress;
    }

    private String userAgent(HttpServerRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
