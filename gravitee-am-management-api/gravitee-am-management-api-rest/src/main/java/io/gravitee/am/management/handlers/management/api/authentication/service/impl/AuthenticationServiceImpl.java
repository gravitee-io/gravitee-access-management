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
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.EndUserAuthentication;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.model.*;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    /**
     * Constant to use while setting identity provider used to authenticate a user
     */
    public static final String SOURCE = "source";

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private AuditService auditService;

    @Override
    public User onAuthenticationSuccess(Authentication auth) {
        final DefaultUser principal = (DefaultUser) auth.getPrincipal();

        final EndUserAuthentication authentication = new EndUserAuthentication(principal.getUsername(), null, new SimpleAuthenticationContext());
        Map<String, String> details = auth.getDetails() == null ? new HashMap<>() : new HashMap<>((Map<String, String>) auth.getDetails());

        details.putIfAbsent(Claims.organization, Organization.DEFAULT);

        String organizationId = details.get(Claims.organization);

        final String source = details.get(SOURCE);
        io.gravitee.am.model.User endUser = userService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, organizationId, principal.getId(), source)
                .switchIfEmpty(Maybe.defer(() -> userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, organizationId, principal.getUsername(), source)))
                .switchIfEmpty(Maybe.error(new UserNotFoundException(principal.getUsername())))
                .flatMapSingle(existingUser -> {
                    existingUser.setSource(details.get(SOURCE));
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
                    if(existingUser.getAdditionalInformation() != null) {
                        existingUser.getAdditionalInformation().putAll(principal.getAdditionalInformation());
                    } else {
                        existingUser.setAdditionalInformation(new HashMap<>(principal.getAdditionalInformation()));
                    }
                    return userService.update(existingUser);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        final io.gravitee.am.model.User newUser = new io.gravitee.am.model.User();
                        newUser.setInternal(false);
                        newUser.setExternalId(principal.getId());
                        newUser.setUsername(principal.getUsername());
                        newUser.setSource(details.get(SOURCE));
                        newUser.setReferenceType(ReferenceType.ORGANIZATION);
                        newUser.setReferenceId(organizationId);
                        newUser.setLoggedAt(new Date());
                        newUser.setLoginsCount(1l);
                        newUser.setAdditionalInformation(principal.getAdditionalInformation());
                        return userService.create(newUser)
                                .flatMap(user -> setRoles(principal, user)
                                        .map(membership -> user));
                    }
                    return Single.error(ex);
                })
                .flatMap(userService::enhance)
                .doOnSuccess(user -> auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class).principal(authentication).referenceType(ReferenceType.ORGANIZATION)
                        .referenceId(organizationId).user(user)))
                .blockingGet();

        principal.setId(endUser.getId());

        if(endUser.getAdditionalInformation()!= null) {
            principal.getAdditionalInformation().putAll(endUser.getAdditionalInformation());
        }

        principal.getAdditionalInformation().put(StandardClaims.SUB, endUser.getId());
        principal.getAdditionalInformation().put(Claims.organization, endUser.getReferenceId());
        principal.getAdditionalInformation().put("login_count", endUser.getLoginsCount());

        // set roles
        Set<String> roles = endUser.getRoles() != null ? new HashSet<>(endUser.getRoles()) : new HashSet<>();
        if (principal.getRoles() != null) {
            roles.addAll(principal.getRoles());
        }

        principal.getAdditionalInformation().put(CustomClaims.ROLES, roles);

        return principal;
    }

    /**
     * Set the ORGANIZATION_USER role to a newly create user.
     * Note: this business code should not be here and will be moved to a dedicated UserService when the following issue
     * will be handled https://github.com/gravitee-io/issues/issues/3323
     */
    private Single<Membership> setRoles(User principal, io.gravitee.am.model.User user) {

        final Maybe<Role> defaultRoleObs = roleService.findDefaultRole(user.getReferenceId(), DefaultRole.ORGANIZATION_USER, ReferenceType.ORGANIZATION);
        Maybe<Role> roleObs = defaultRoleObs;

        if (principal.getRoles() != null && !principal.getRoles().isEmpty()) {
            // We allow only one role in AM portal. Get the first (should not append).
            String roleId = principal.getRoles().get(0);

            roleObs = roleService.findById(user.getReferenceType(), user.getReferenceId(), roleId)
                    .toMaybe()
                    .onErrorResumeNext(throwable -> {
                        if (throwable instanceof RoleNotFoundException) {
                            return roleService.findById(ReferenceType.PLATFORM, Platform.DEFAULT, roleId).toMaybe()
                                    .switchIfEmpty(defaultRoleObs)
                                    .onErrorResumeNext(defaultRoleObs);
                        } else {
                            return defaultRoleObs;
                        }
                    });
        }

        Membership membership = new Membership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(user.getId());
        membership.setReferenceType(user.getReferenceType());
        membership.setReferenceId(user.getReferenceId());

        return roleObs.switchIfEmpty(Maybe.error(new TechnicalManagementException(String.format("Cannot add user membership to organization %s. Unable to find ORGANIZATION_USER role", user.getReferenceId()))))
                .flatMapSingle(role -> {
                    membership.setRoleId(role.getId());
                    return membershipService.addOrUpdate(user.getReferenceId(), membership);
                });
    }
}
