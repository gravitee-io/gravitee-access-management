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
package io.gravitee.am.management.handlers.management.api.authentication.service.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.EndUserAuthentication;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.ManagementAuthenticationContext;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    /**
     * Constant to use while setting identity provider used to authenticate a user
     */
    private static final String SOURCE = "source";
    private static final String CLIENT_ID = "admin";

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    @Override
    public User onAuthenticationSuccess(Authentication auth) {
        final DefaultUser principal = (DefaultUser) auth.getPrincipal();

        ManagementAuthenticationContext authenticationContext = new ManagementAuthenticationContext();
        Map<String, String> details = auth.getDetails() == null ? new HashMap<>() : new HashMap<>((Map) auth.getDetails());
        details.forEach(authenticationContext::set);
        String organizationId = "DEFAULT";
        authenticationContext.set("organization", organizationId);

        final EndUserAuthentication authentication = new EndUserAuthentication(principal.getUsername(), null, authenticationContext);

        final String source = details.get(SOURCE);
        io.gravitee.am.model.User endUser = userService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, organizationId, principal.getId(), source)
                .switchIfEmpty(Maybe.defer(() -> userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, organizationId, principal.getUsername(), source)))
                .switchIfEmpty(Maybe.error(new UserNotFoundException(principal.getUsername())))
                .flatMapSingle(existingUser -> {
                    existingUser.setSource(details.get(SOURCE));
                    existingUser.setClient(CLIENT_ID);
                    existingUser.setLoggedAt(new Date());
                    existingUser.setLoginsCount(existingUser.getLoginsCount() + 1);
                    // set roles
                    if (existingUser.getRoles() == null) {
                        existingUser.setRoles(principal.getRoles());
                    } else if (principal.getRoles() != null) {
                        // filter roles
                        principal.getRoles().removeAll(existingUser.getRoles());
                        existingUser.getRoles().addAll(principal.getRoles());
                    }
                    existingUser.setAdditionalInformation(principal.getAdditionalInformation());
                    return userService.update(existingUser);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        final io.gravitee.am.model.User newUser = new io.gravitee.am.model.User();
                        newUser.setInternal(false);
                        newUser.setUsername(principal.getUsername());
                        newUser.setSource(details.get(SOURCE));
                        newUser.setClient(CLIENT_ID);
                        newUser.setReferenceType(ReferenceType.ORGANIZATION);
                        newUser.setReferenceId(organizationId);
                        newUser.setLoggedAt(new Date());
                        newUser.setLoginsCount(1l);
                        newUser.setRoles(principal.getRoles());
                        newUser.setAdditionalInformation(principal.getAdditionalInformation());
                        return userService.create(newUser);
                    }
                    return Single.error(ex);
                })
                .flatMap(userService::enhance)
                .doOnSuccess(user -> auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class).principal(authentication).referenceType(ReferenceType.ORGANIZATION).referenceId(organizationId).client(CLIENT_ID).user(user)))
                .blockingGet();

        principal.setId(endUser.getId());
        principal.getAdditionalInformation().put(StandardClaims.SUB, endUser.getId());

        if(endUser.getReferenceType() == ReferenceType.ORGANIZATION) {
            principal.getAdditionalInformation().put(Claims.organization, endUser.getReferenceId());
        }

        // set roles
        Set<String> roles = endUser.getRoles() != null ? new HashSet<>(endUser.getRoles()) : new HashSet<>();
        if (principal.getRoles() != null) {
            roles.addAll(principal.getRoles());
        }

        principal.getAdditionalInformation().put(CustomClaims.ROLES, roles);

        return principal;
    }
}
