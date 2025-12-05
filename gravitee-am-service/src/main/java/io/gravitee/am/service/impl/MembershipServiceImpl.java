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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.Member;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.MembershipRepository;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.am.service.exception.MembershipNotFoundException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.SinglePrimaryOwnerException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewMembership;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.MembershipAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MembershipServiceImpl implements MembershipService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MembershipServiceImpl.class);

    @Lazy
    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private OrganizationUserService orgUserService;

    @Autowired
    private OrganizationGroupService orgGroupService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private EventService eventService;

    @Override
    public Maybe<Membership> findById(String id) {
        LOGGER.debug("Find membership by ID {}", id);
        return membershipRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find membership by id {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error occurs while trying to find membership by ID %s", id), ex));
                });
    }

    @Override
    public Flowable<Membership> findByCriteria(ReferenceType referenceType, String referenceId, MembershipCriteria criteria) {
        LOGGER.debug("Find memberships by reference type {} and referenceId {} and criteria {}", referenceType, referenceId, criteria);

        return membershipRepository.findByCriteria(referenceType, referenceId, criteria);
    }

    @Override
    public Flowable<Membership> findByCriteria(ReferenceType referenceType, MembershipCriteria criteria) {
        LOGGER.debug("Find memberships by reference type {} and criteria {}", referenceType, criteria);

        return membershipRepository.findByCriteria(referenceType, criteria);
    }

    @Override
    public Flowable<Membership> findByReference(String referenceId, ReferenceType referenceType) {
        LOGGER.debug("Find memberships by reference id {} and reference type {}", referenceId, referenceType);
        return membershipRepository.findByReference(referenceId, referenceType)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find memberships by reference id {} and reference type {}", referenceId, referenceType, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find memberships by reference id %s and reference type %s", referenceId, referenceType), ex));
                });
    }

    @Override
    public Flowable<Membership> findByMember(String memberId, MemberType memberType) {
        LOGGER.debug("Find memberships by member id {} and member type {}", memberId, memberType);
        return membershipRepository.findByMember(memberId, memberType)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find memberships by member id {} and member type {}", memberId, memberType, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find memberships by member id %s and member type %s", memberId, memberType), ex));
                });
    }

    @Override
    public Single<Membership> addOrUpdate(String organizationId, Membership membership, User principal) {
        LOGGER.debug("Add or update membership {}", membership);

        return checkMember(organizationId, membership)
                .andThen(checkRole(organizationId, membership))
                .andThen(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(optMembership -> {
                            if (!optMembership.isPresent()) {
                                // add membership
                                Membership newMembership = new Membership();
                                newMembership.setId(RandomString.generate());
                                newMembership.setDomain(membership.getDomain());
                                newMembership.setMemberId(membership.getMemberId());
                                newMembership.setMemberType(membership.getMemberType());
                                newMembership.setReferenceId(membership.getReferenceId());
                                newMembership.setReferenceType(membership.getReferenceType());
                                newMembership.setRoleId(membership.getRoleId());
                                newMembership.setCreatedAt(new Date());
                                newMembership.setUpdatedAt(newMembership.getCreatedAt());
                                newMembership.setFromRoleMapper(membership.isFromRoleMapper());
                                return createInternal(newMembership, principal);
                            } else {
                                // update membership
                                Membership oldMembership = optMembership.get();
                                Membership updateMembership = new Membership(oldMembership);
                                updateMembership.setRoleId(membership.getRoleId());
                                updateMembership.setUpdatedAt(new Date());
                                updateMembership.setFromRoleMapper(membership.isFromRoleMapper());
                                return membershipRepository.update(updateMembership)
                                        // create event for sync process
                                        .flatMap(membership1 -> {
                                            Event event = new Event(Type.MEMBERSHIP, new Payload(membership1.getId(), membership1.getReferenceType(), membership1.getReferenceId(), Action.UPDATE));
                                            return eventService.create(event).flatMap(__ -> Single.just(membership1));
                                        })
                                        .onErrorResumeNext(ex -> {
                                            if (ex instanceof AbstractManagementException) {
                                                return Single.error(ex);
                                            }
                                            LOGGER.error("An error occurs while trying to update membership {}", oldMembership, ex);
                                            return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to update membership %s", oldMembership), ex));
                                        })
                                        .doOnSuccess(membership1 -> auditService.report(AuditBuilder.builder(MembershipAuditBuilder.class).principal(principal).type(EventType.MEMBERSHIP_UPDATED).oldValue(oldMembership).membership(membership1)))
                                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(MembershipAuditBuilder.class).principal(principal).reference(new Reference(membership.getReferenceType(), membership.getReferenceId())).type(EventType.MEMBERSHIP_UPDATED).throwable(throwable)));
                            }
                        })
                );
    }

    @Override
    public Single<Membership> setPlatformAdmin(String userId) {

        MembershipCriteria criteria = new MembershipCriteria();
        criteria.setUserId(userId);

        return roleService.findSystemRole(SystemRole.PLATFORM_ADMIN, ReferenceType.PLATFORM)
                .switchIfEmpty(Single.error(new RoleNotFoundException(SystemRole.PLATFORM_ADMIN.name())))
                .flatMap(role -> findByCriteria(ReferenceType.PLATFORM, Platform.DEFAULT, criteria).firstElement()
                        .switchIfEmpty(Single.defer(() -> {
                            final Date now = new Date();
                            Membership membership = new Membership();
                            membership.setRoleId(role.getId());
                            membership.setMemberType(MemberType.USER);
                            membership.setMemberId(userId);
                            membership.setReferenceType(ReferenceType.PLATFORM);
                            membership.setReferenceId(Platform.DEFAULT);
                            membership.setCreatedAt(now);
                            membership.setUpdatedAt(now);

                            return createInternal(membership, null);
                        })));
    }


    @Override
    public Single<Map<String, Map<String, Object>>> getMetadata(List<Membership> memberships) {
        if (memberships == null || memberships.isEmpty()) {
            return Single.just(Collections.emptyMap());
        }

        List<String> userIds = memberships.stream().filter(membership -> MemberType.USER.equals(membership.getMemberType())).map(Membership::getMemberId).distinct().collect(Collectors.toList());
        List<String> groupIds = memberships.stream().filter(membership -> MemberType.GROUP.equals(membership.getMemberType())).map(Membership::getMemberId).distinct().collect(Collectors.toList());
        List<String> roleIds = memberships.stream().map(Membership::getRoleId).distinct().collect(Collectors.toList());

        return Single.zip(orgUserService.findByIdIn(userIds).toMap(io.gravitee.am.model.User::getId, this::convert),
                orgGroupService.findByIdIn(groupIds).toMap(Group::getId, this::convert),
                roleService.findByIdIn(roleIds), (users, groups, roles) -> {
            Map<String, Map<String, Object>> metadata = new HashMap<>();
            metadata.put("users", (Map)users);
            metadata.put("groups", (Map)groups);
            metadata.put("roles", roles.stream().collect(Collectors.toMap(Role::getId, this::filter)));
            return metadata;
        });
    }

    @Override
    public Completable delete(String membershipId, User principal) {
        LOGGER.debug("Delete membership {}", membershipId);

        return membershipRepository.findById(membershipId)
                .switchIfEmpty(Maybe.error(new MembershipNotFoundException(membershipId)))
                .flatMapCompletable(membership -> membershipRepository.delete(membershipId)
                        .andThen(Completable.fromSingle(eventService.create(new Event(Type.MEMBERSHIP, new Payload(membership.getId(), membership.getReferenceType(), membership.getReferenceId(), Action.DELETE)))))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(MembershipAuditBuilder.class)
                                .principal(principal)
                                .type(EventType.MEMBERSHIP_DELETED)
                                .membership(membership)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(MembershipAuditBuilder.class)
                                .principal(principal).type(EventType.MEMBERSHIP_DELETED)
                                .membership(membership)
                                .throwable(throwable)))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete membership: {}", membershipId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete membership: %s", membershipId), ex));
                });
    }

    @Override
    public Completable addDomainUserRoleIfNecessary(String organizationId, String environmentId, String domainId, NewMembership newMembership, User principal) {

        MembershipCriteria criteria = convert(newMembership);

        return this.findByCriteria(ReferenceType.DOMAIN, domainId, criteria)
                .switchIfEmpty(Flowable.defer(() -> roleService.findDefaultRole(organizationId, DefaultRole.DOMAIN_USER, ReferenceType.DOMAIN)
                        .flatMapSingle(role -> {
                            final Membership domainMembership = new Membership();
                            domainMembership.setMemberId(newMembership.getMemberId());
                            domainMembership.setMemberType(newMembership.getMemberType());
                            domainMembership.setRoleId(role.getId());
                            domainMembership.setDomain(domainId);
                            domainMembership.setReferenceId(domainId);
                            domainMembership.setReferenceType(ReferenceType.DOMAIN);
                            return this.createInternal(domainMembership, principal);
                        }).toFlowable()))
                .ignoreElements()
                .andThen(addEnvironmentUserRoleIfNecessary(organizationId, environmentId, newMembership, principal));
    }

    @Override
    public Completable addEnvironmentUserRoleIfNecessary(String organizationId, String environmentId, NewMembership newMembership, User principal) {

        MembershipCriteria criteria = convert(newMembership);

        return this.findByCriteria(ReferenceType.ENVIRONMENT, environmentId, criteria)
                .switchIfEmpty(Flowable.defer(() -> roleService.findDefaultRole(organizationId, DefaultRole.ENVIRONMENT_USER, ReferenceType.ENVIRONMENT)
                        .flatMapSingle(role -> {
                            final Membership environmentMembership = new Membership();
                            environmentMembership.setMemberId(newMembership.getMemberId());
                            environmentMembership.setMemberType(newMembership.getMemberType());
                            environmentMembership.setRoleId(role.getId());
                            environmentMembership.setReferenceId(environmentId);
                            environmentMembership.setReferenceType(ReferenceType.ENVIRONMENT);
                            return this.createInternal(environmentMembership, principal);
                        }).toFlowable()))
                .ignoreElements();
    }

    private Single<Membership> createInternal(Membership membership, User principal) {
        return membershipRepository.create(membership)
                // create event for sync process
                .flatMap(membership1 -> {
                    Event event = new Event(Type.MEMBERSHIP, new Payload(membership1.getId(), membership1.getReferenceType(), membership1.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(membership1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create membership {}", membership, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to create membership %s", membership), ex));
                })
                .doOnSuccess(membership1 -> auditService.report(AuditBuilder.builder(MembershipAuditBuilder.class).principal(principal).type(EventType.MEMBERSHIP_CREATED).membership(membership1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(MembershipAuditBuilder.class).principal(principal).type(EventType.MEMBERSHIP_CREATED).reference(new Reference(membership.getReferenceType(), membership.getReferenceId())).throwable(throwable)));
    }

    private Member convert(io.gravitee.am.model.User user) {
        Member member = new Member();
        member.setId(user.getId());
        member.setDisplayName(user.getDisplayName());
        return member;
    }

    private Member convert(Group group) {
        Member member = new Member();
        member.setId(group.getId());
        member.setDisplayName(group.getName());
        return member;
    }

    private MembershipCriteria convert(NewMembership newMembership) {

        MembershipCriteria criteria = new MembershipCriteria();

        if (newMembership.getMemberType() == MemberType.USER) {
            criteria.setUserId(newMembership.getMemberId());
        } else {
            criteria.setGroupIds(Collections.singletonList(newMembership.getMemberId()));
        }
        return criteria;
    }

    private Role filter(Role role) {
        Role filteredRole = new Role();
        filteredRole.setId(role.getId());
        filteredRole.setName(role.getName());
        return filteredRole;
    }

    /**
     * Member must exist and be part of the organization users/groups
     * @param membership
     * @return
     */
    private Completable checkMember(String organizationId, Membership membership) {

        if (MemberType.USER.equals(membership.getMemberType())) {
            return orgUserService.findById(ReferenceType.ORGANIZATION, organizationId, membership.getMemberId())
                    .ignoreElement();
        } else {
            return orgGroupService.findById(organizationId, membership.getMemberId())
                    .ignoreElement();
        }
    }

    /**
     * Role must exists and be of the platform roles (i.e master domain)
     * @param membership the membership to check role on.
     * @return
     */
    private Completable checkRole(String organizationId, Membership membership) {
        return roleService.findById(membership.getRoleId())
                .switchIfEmpty(Maybe.error(new RoleNotFoundException(membership.getRoleId())))
                .flatMap(role -> {
                    // If role is a 'PRIMARY_OWNER' role, need to check if it is already assigned or not.
                    if (role.isSystem() && role.getName().endsWith("_PRIMARY_OWNER")) {

                        if (membership.getMemberType() == MemberType.GROUP) {
                            return Maybe.error(new InvalidRoleException("This role cannot be assigned to a group"));
                        }

                        MembershipCriteria criteria = new MembershipCriteria();
                        criteria.setRoleId(membership.getRoleId());
                        return membershipRepository.findByCriteria(membership.getReferenceType(), membership.getReferenceId(), criteria)
                                .filter(existingMembership -> !existingMembership.isMember(membership.getMemberType(), membership.getMemberId())) // Exclude the member himself if he is already the primary owner.
                                .count()
                                .flatMapMaybe(count -> count >= 1 ? Maybe.error(new SinglePrimaryOwnerException(membership.getReferenceType())) : Maybe.just(role));
                    }

                    return Maybe.just(role);
                })
                // Role must be set on the right entity type.
                .filter(role1 -> role1.getAssignableType().equals(membership.getReferenceType()) &&
                        // Role can be either a system role, either an organization role, either a domain role.
                        (role1.isSystem()
                                || (role1.getReferenceType() == ReferenceType.ORGANIZATION && organizationId.equals(role1.getReferenceId()))
                                || (role1.getReferenceType() == membership.getReferenceType() && membership.getReferenceId().equals(role1.getReferenceId()))))
                .switchIfEmpty(Single.error(new InvalidRoleException("Invalid role")))
                .ignoreElement();
    }
}
