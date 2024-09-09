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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.users.service.DomainUserConsentService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AbstractUserConsentEndpointHandler {

    protected final DomainUserConsentService userService;
    private final ClientSyncService clientSyncService;
    private final Domain domain;
    private final SubjectManager subjectManager;

    public AbstractUserConsentEndpointHandler(DomainUserConsentService userService, ClientSyncService clientSyncService, Domain domain, SubjectManager subjectManager) {
        this.userService = userService;
        this.clientSyncService = clientSyncService;
        this.domain = domain;
        this.subjectManager = subjectManager;
    }

    protected Single<User> getPrincipal(RoutingContext context) {
        JWT token = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);

        if (token.getSub() == null) {
            return Single.just(defaultPrincipal(context, token));
        }

        // end user
        if (!token.getSub().equals(token.getAud())) {
            return subjectManager.findUserBySub(token)
                    .map(user -> {
                        DefaultUser principal = new DefaultUser(user.getUsername());
                        principal.setId(user.getId());
                        Map<String, Object> additionalInformation =
                                user.getAdditionalInformation() != null ? new HashMap<>(user.getAdditionalInformation()) : new HashMap<>();
                        // add ip address and user agent
                        if (canSaveIp(context)) {
                            additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(context.request()));
                        }
                        if (canSaveIp(context)) {
                            additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(context.request()));
                        }
                        additionalInformation.put(Claims.DOMAIN, domain.getId());
                        principal.setAdditionalInformation(additionalInformation);
                        return (User) principal;
                    })
                    .defaultIfEmpty(defaultPrincipal(context, token));
        } else {
            // revocation made oauth2 clients
            return clientSyncService.findByClientId(token.getAud())
                    .map(client -> {
                        DefaultUser principal = new DefaultUser(client.getClientId());
                        principal.setId(client.getId());
                        Map<String, Object> additionalInformation = new HashMap<>();
                        // add ip address and user agent
                        if (canSaveIp(context)) {
                            additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(context.request()));
                        }
                        if (canSaveUserAgent(context)) {
                            additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(context.request()));
                        }
                        additionalInformation.put(Claims.DOMAIN, domain.getId());
                        principal.setAdditionalInformation(additionalInformation);
                        return (User) principal;
                    })
                    .defaultIfEmpty(defaultPrincipal(context, token));
        }

    }

    private User defaultPrincipal(RoutingContext context, JWT token) {
        final String username = token.getSub() != null ? token.getSub() : "unknown-user";
        DefaultUser principal = new DefaultUser(username);
        principal.setId(username);
        Map<String, Object> additionalInformation = new HashMap<>();
        // add ip address and user agent
        additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(context.request()));
        additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(context.request()));
        additionalInformation.put(Claims.DOMAIN, domain.getId());
        principal.setAdditionalInformation(additionalInformation);
        return principal;
    }

    protected Single<UserId> getUserIdFromSub(JWT token) {
        return this.subjectManager.findUserIdBySub(token)
                .onErrorResumeNext((err) -> err instanceof IllegalArgumentException ? Maybe.just(UserId.internal(token.getSub())) : Maybe.error(err))
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(token.getSub())));
    }


    protected boolean userIdParamMatchTokenIdentity(UserId idFromSub, String requestedUserId, JWT accessToken) {
        var sameUser = requestedUserId.equals(idFromSub.id());
        var matchesGis = requestedUserId.equals(this.subjectManager.generateInternalSubFrom(idFromSub));
        var matchesSub = requestedUserId.equals(accessToken.getSub());
        return sameUser || matchesSub || matchesGis;
    }
}
