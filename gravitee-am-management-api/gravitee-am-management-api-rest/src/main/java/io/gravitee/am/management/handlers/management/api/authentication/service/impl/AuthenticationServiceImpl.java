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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.EndUserAuthentication;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
    public static final String SOURCE = "source";
    private static final String IP_ADDRESS_KEY = "ip_address";
    private static final String USER_AGENT_KEY = "user_agent";

    @Autowired
    private OrganizationUserService userService;

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
                .switchIfEmpty(Single.error(new UserNotFoundException(principal.getUsername())))
                .flatMap(existingUser -> {
                    existingUser.setSource(details.get(SOURCE));
                    existingUser.setLoggedAt(new Date());
                    existingUser.setLoginsCount(existingUser.getLoginsCount() + 1);
                    if (principal.getEmail() != null) {
                        existingUser.setEmail(principal.getEmail());
                    }
                    if (existingUser.getAdditionalInformation() != null) {
                        existingUser.getAdditionalInformation().putAll(principal.getAdditionalInformation());
                    } else {
                        existingUser.setAdditionalInformation(new HashMap<>(principal.getAdditionalInformation()));
                    }
                    return userService.update(existingUser)
                            .flatMap(user -> updateRoles(principal, existingUser).andThen(Single.just(user)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        final io.gravitee.am.model.User newUser = new io.gravitee.am.model.User();
                        newUser.setInternal(false);
                        newUser.setExternalId(principal.getId());
                        newUser.setUsername(principal.getUsername());
                        if (principal.getEmail() != null) {
                            newUser.setEmail(principal.getEmail());
                        }
                        newUser.setSource(details.get(SOURCE));
                        newUser.setReferenceType(ReferenceType.ORGANIZATION);
                        newUser.setReferenceId(organizationId);
                        newUser.setLoggedAt(new Date());
                        newUser.setLoginsCount(1L);
                        newUser.setAdditionalInformation(principal.getAdditionalInformation());
                        return userService.create(newUser)
                                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).user(user1)))
                                .doOnError(err -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).throwable(err)))
                                .flatMap(user -> userService.setRoles(principal, user).andThen(Single.just(user)));
                    }
                    return Single.error(ex);
                })
                .flatMap(userService::enhance)
                .doOnSuccess(user -> auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class)
                        .principal(authentication)
                        .referenceType(ReferenceType.ORGANIZATION)
                        .referenceId(organizationId).user(user)
                        .ipAddress(details.get(IP_ADDRESS_KEY))
                        .userAgent(details.get(USER_AGENT_KEY)))
                )
                .blockingGet();


        principal.setId(endUser.getId());
        principal.setUsername(endUser.getUsername());

        if (endUser.getAdditionalInformation()!= null) {
            principal.getAdditionalInformation().putAll(endUser.getAdditionalInformation());
        }

        principal.getAdditionalInformation().put(StandardClaims.SUB, endUser.getId());
        principal.getAdditionalInformation().put(StandardClaims.PREFERRED_USERNAME, endUser.getUsername());
        principal.getAdditionalInformation().put(Claims.organization, endUser.getReferenceId());
        principal.getAdditionalInformation().put("login_count", endUser.getLoginsCount());
        principal.getAdditionalInformation().computeIfAbsent(StandardClaims.EMAIL, val -> endUser.getEmail());
        principal.getAdditionalInformation().computeIfAbsent(StandardClaims.NAME, val -> endUser.getDisplayName());

        // set roles
        Set<String> roles = endUser.getRoles() != null ? new HashSet<>(endUser.getRoles()) : new HashSet<>();
        if (principal.getRoles() != null) {
            roles.addAll(principal.getRoles());
        }

        principal.getAdditionalInformation().put(CustomClaims.ROLES, roles);

        return principal;
    }

    /**
     * Update ORGANIZATION role to an existing user if the identity provider role mapper has changed
     */
    private Completable updateRoles(User principal, io.gravitee.am.model.User existingUser) {
        // no role defined, continue
        if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
            return Completable.complete();
        }

        // role to update if it's different from the current one
        final String roleId = principal.getRoles().get(0);

        // update membership if necessary
        return membershipService.findByMember(existingUser.getId(), MemberType.USER)
                .filter(membership -> ReferenceType.ORGANIZATION == membership.getReferenceType())
                .firstElement()
                .map(membership -> !membership.getRoleId().equals(roleId))
                .switchIfEmpty(Maybe.just(false))
                .flatMapCompletable(mustChangeOrganizationRole -> {

                    if (!mustChangeOrganizationRole) {
                        return Completable.complete();
                    }

                    Membership membership = new Membership();
                    membership.setMemberType(MemberType.USER);
                    membership.setMemberId(existingUser.getId());
                    membership.setReferenceType(existingUser.getReferenceType());
                    membership.setReferenceId(existingUser.getReferenceId());
                    membership.setRoleId(roleId);

                    // check role and then update membership
                    return roleService.findById(existingUser.getReferenceType(), existingUser.getReferenceId(), roleId)
                            .flatMap(__ -> membershipService.addOrUpdate(existingUser.getReferenceId(), membership))
                            .ignoreElement();
                });
    }
}
