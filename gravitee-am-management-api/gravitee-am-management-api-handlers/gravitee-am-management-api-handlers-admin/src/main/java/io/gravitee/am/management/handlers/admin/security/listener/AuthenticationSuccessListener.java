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
package io.gravitee.am.management.handlers.admin.security.listener;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.admin.provider.security.EndUserAuthentication;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationSuccessListener implements ApplicationListener<AuthenticationSuccessEvent> {

    /**
     * Constant to use while setting identity provider used to authenticate a user
     */
    private static final String SOURCE = "source";
    private static final String CLIENT_ID = "admin";

    @Autowired
    private Domain domain;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        final User principal = (User) event.getAuthentication().getPrincipal();
        Map<String, String> details = event.getAuthentication().getDetails() == null ? new HashMap<>() : new HashMap<>((Map) event.getAuthentication().getDetails());
        details.put(Claims.domain, domain.getId());

        final Authentication authentication = new EndUserAuthentication(((User) event.getAuthentication().getPrincipal()).getUsername(), null);
        ((EndUserAuthentication) authentication).setAdditionalInformation((Map) details);

        userService.findByDomainAndUsername(domain.getId(), principal.getUsername())
                .switchIfEmpty(Maybe.error(new UserNotFoundException(principal.getUsername())))
                .flatMapSingle(user -> {
                    UpdateUser updateUser = new UpdateUser();
                    if (details != null) {
                        updateUser.setSource(details.get(SOURCE));
                        updateUser.setClient(CLIENT_ID);
                    }
                    updateUser.setLoggedAt(new Date());
                    updateUser.setLoginsCount(user.getLoginsCount() + 1);
                    updateUser.setAdditionalInformation(principal.getAdditionalInformation());
                    return userService.update(domain.getId(), user.getId(), updateUser);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        final NewUser newUser = new NewUser();
                        newUser.setInternal(false);
                        newUser.setUsername(principal.getUsername());
                        if (details != null) {
                            newUser.setSource(details.get(SOURCE));
                            newUser.setClient(CLIENT_ID);
                        }
                        newUser.setLoggedAt(new Date());
                        newUser.setLoginsCount(1l);
                        newUser.setAdditionalInformation(principal.getAdditionalInformation());
                        return userService.create(domain.getId(), newUser);
                    }
                    return Single.error(ex);
                })
                .doOnSuccess(user -> auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class).principal(authentication).domain(domain.getId()).client(CLIENT_ID).user(user)))
                .subscribe();
    }
}
