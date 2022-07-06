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
package io.gravitee.am.gateway.handler.users.resources.consents;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.users.service.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.impl.user.activity.utils.ConsentUtils;
import io.reactivex.Single;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;

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
        JWT token = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);

        if (token.getSub() == null) {
            return Single.just(defaultPrincipal(context, token));
        }

        // end user
        if (!token.getSub().equals(token.getAud())) {
            return userService.findById(token.getSub())
                    .map(user -> {
                        User principal = new DefaultUser(user.getUsername());
                        ((DefaultUser) principal).setId(user.getId());
                        Map<String, Object> additionalInformation =
                                user.getAdditionalInformation() != null ? new HashMap<>(user.getAdditionalInformation()) : new HashMap<>();
                        // add ip address and user agent
                        if (canSaveIp(context)) {
                            additionalInformation.put(Claims.ip_address, RequestUtils.remoteAddress(context.request()));
                        }
                        if (canSaveIp(context)) {
                            additionalInformation.put(Claims.user_agent, RequestUtils.userAgent(context.request()));
                        }
                        additionalInformation.put(Claims.domain, domain.getId());
                        ((DefaultUser) principal).setAdditionalInformation(additionalInformation);
                        return principal;
                    })
                    .defaultIfEmpty(defaultPrincipal(context, token))
                    .toSingle();
        } else {
            // revocation made oauth2 clients
            return clientSyncService.findByClientId(token.getAud())
                    .map(client -> {
                        User principal = new DefaultUser(client.getClientId());
                        ((DefaultUser) principal).setId(client.getId());
                        Map<String, Object> additionalInformation = new HashMap<>();
                        // add ip address and user agent
                        if (canSaveIp(context)) {
                            additionalInformation.put(Claims.ip_address, RequestUtils.remoteAddress(context.request()));
                        }
                        if (canSaveUserAgent(context)) {
                            additionalInformation.put(Claims.user_agent, RequestUtils.userAgent(context.request()));
                        }
                        additionalInformation.put(Claims.domain, domain.getId());
                        ((DefaultUser) principal).setAdditionalInformation(additionalInformation);
                        return principal;
                    })
                    .defaultIfEmpty(defaultPrincipal(context, token))
                    .toSingle();
        }

    }

    private User defaultPrincipal(RoutingContext context, JWT token) {
        final String username = token.getSub() != null ? token.getSub() : "unknown-user";
        User principal = new DefaultUser(username);
        ((DefaultUser) principal).setId(username);
        Map<String, Object> additionalInformation = new HashMap<>();
        // add ip address and user agent
        additionalInformation.put(Claims.ip_address, RequestUtils.remoteAddress(context.request()));
        additionalInformation.put(Claims.user_agent, RequestUtils.userAgent(context.request()));
        additionalInformation.put(Claims.domain, domain.getId());
        ((DefaultUser) principal).setAdditionalInformation(additionalInformation);
        return principal;
    }

}
