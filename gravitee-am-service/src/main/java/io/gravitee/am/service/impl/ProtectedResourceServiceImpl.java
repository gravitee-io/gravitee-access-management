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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.*;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.am.service.model.ProtectedResourceSecret;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ProtectedResourceAuditBuilder;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

import static io.gravitee.am.model.ProtectedResource.Type.valueOf;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.hasText;

@Component
public class ProtectedResourceServiceImpl implements ProtectedResourceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtectedResourceServiceImpl.class);

    @Autowired
    @Lazy
    private ProtectedResourceRepository repository;

    @Autowired
    private ApplicationSecretConfig applicationSecretConfig;

    @Autowired
    private SecretService secretService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private OAuthClientUniquenessValidator oAuthClientUniquenessValidator;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Override
    public Single<ProtectedResourceSecret> create(Domain domain, User principal, NewProtectedResource newProtectedResource) {
        LOGGER.debug("Create ProtectedResource {}", newProtectedResource);
        ProtectedResource toCreate = new ProtectedResource();
        toCreate.setCreatedAt(new Date());
        toCreate.setUpdatedAt(toCreate.getCreatedAt());
        toCreate.setDomainId(domain.getId());
        toCreate.setType(valueOf(newProtectedResource.getType()));

        var secretSettings = this.applicationSecretConfig.toSecretSettings();
        var rawSecret = hasLength(newProtectedResource.getClientSecret()) ? newProtectedResource.getClientSecret() : SecureRandomString.generate();

        toCreate.setId(RandomString.generate());
        toCreate.setName(newProtectedResource.getName());
        toCreate.setDescription(newProtectedResource.getDescription());
        toCreate.setResourceIdentifiers(newProtectedResource.getResourceIdentifiers());
        toCreate.setClientId(hasLength(newProtectedResource.getClientId()) ? newProtectedResource.getClientId() : SecureRandomString.generate());

        toCreate.setSecretSettings(List.of(secretSettings));
        toCreate.setClientSecrets(List.of(buildClientSecret(domain, secretSettings, rawSecret)));

        return oAuthClientUniquenessValidator.checkClientIdUniqueness(domain.getId(), toCreate.getClientId())
                .andThen(doCreate(toCreate, principal, domain))
                .map(res -> ProtectedResourceSecret.from(res, rawSecret));
    }

    private Single<ProtectedResource> doCreate(ProtectedResource toCreate, User principal, Domain domain) {
        return repository.create(toCreate)
                .flatMap(protectedResource -> {
                    Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(protectedResource.getId(), ReferenceType.DOMAIN, protectedResource.getDomainId(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(e -> Single.just(protectedResource));
                })
                .doOnSuccess(created -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).protectedResource(created).principal(principal).type(EventType.PROTECTED_RESOURCE_CREATED)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).protectedResource(toCreate).principal(principal).type(EventType.PROTECTED_RESOURCE_CREATED).throwable(throwable)))
                .flatMap(protectedResource -> {
                    if (principal == null || principal.getAdditionalInformation() == null || !hasText((String) principal.getAdditionalInformation().get(Claims.ORGANIZATION))) {
                        // There is no principal or we can not find the organization the user is attached to. Can't assign role.
                        return Single.just(protectedResource);
                    }

                    return roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)
                            .switchIfEmpty(Single.error(new InvalidRoleException("Cannot assign owner to the application, owner role does not exist")))
                            .flatMap(role -> {
                                Membership membership = new Membership();
                                membership.setDomain(protectedResource.getDomainId());
                                membership.setMemberId(principal.getId());
                                membership.setMemberType(MemberType.USER);
                                membership.setReferenceId(protectedResource.getId());
                                membership.setReferenceType(ReferenceType.APPLICATION);
                                membership.setRoleId(role.getId());
                                return membershipService.addOrUpdate((String) principal.getAdditionalInformation().get(Claims.ORGANIZATION), membership)
                                        .map(updatedMembership -> protectedResource);
                            });
                });
    }

    private ClientSecret buildClientSecret(Domain domain, ApplicationSecretSettings secretSettings, String rawSecret) {
        return this.secretService.generateClientSecret("Default", rawSecret, secretSettings, domain.getSecretExpirationSettings(), null);
    }
}
