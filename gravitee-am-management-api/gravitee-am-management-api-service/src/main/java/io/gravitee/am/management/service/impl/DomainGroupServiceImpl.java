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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.management.service.DomainGroupService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.GroupAlreadyExistsException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.am.service.model.UpdateGroup;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.GroupAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainGroupServiceImpl implements DomainGroupService {

    private final Logger LOGGER = LoggerFactory.getLogger(DomainGroupServiceImpl.class);

    @Lazy
    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    private AuditService auditService;

    @Autowired
    private RoleService roleService;

    @Override
    public Single<Page<Group>> findAll(Domain domain, int page, int size) {
        LOGGER.debug("Find groups by domain: {}", domain.getId());
        return dataPlaneRegistry.getGroupRepository(domain)
                .findAll(ReferenceType.DOMAIN, domain.getId(), page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups for domain {}", domain.getId(), ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for domain %s", domain.getId()), ex));
                });
    }

    @Override
    public Flowable<Group> findAll(Domain domain) {
        LOGGER.debug("Find groups by domain: {}", domain.getId());
        return dataPlaneRegistry.getGroupRepository(domain)
                .findAll(ReferenceType.DOMAIN, domain.getId())
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups for domain {}", domain.getId(), ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users for domain %s", domain.getId()), ex));
                });
    }

    @Override
    public Maybe<Group> findByName(Domain domain, String groupName) {
        LOGGER.debug("Find group by domain and name: {} {}", domain.getId(), groupName);
        return dataPlaneRegistry.getGroupRepository(domain)
                .findByName(ReferenceType.DOMAIN, domain.getId(), groupName)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its name: {} for the domain {}", groupName, domain.getId(), ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its name: %s for the domain %s", groupName, domain.getId()), ex));
                });
    }

    @Override
    public Single<Group> findById(Domain domain, String id) {
        LOGGER.debug("Find group by id : {}", id);
        return dataPlaneRegistry.getGroupRepository(domain)
                .findById(ReferenceType.DOMAIN, domain.getId(), id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its id {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a group using its id: %s", id), ex));
                })
                .switchIfEmpty(Single.error(new GroupNotFoundException(id)));
    }

    @Override
    public Single<Page<User>> findMembers(Domain domain, String groupId, int page, int size) {
        LOGGER.debug("Find members for group : {}", groupId);
        return findById(domain, groupId)
                .flatMap(group -> {
                    if (group.getMembers() == null || group.getMembers().isEmpty()) {
                        return Single.just(new Page<>(null, page, 0));
                    } else {
                        // get members
                        List<String> sortedMembers = group.getMembers().stream().sorted().collect(Collectors.toList());
                        final int startOffset = page * size;
                        final int endOffset = (page + 1) * size;
                        List<String> pagedMemberIds = sortedMembers.subList(Math.min(sortedMembers.size(), startOffset), Math.min(sortedMembers.size(), endOffset));
                        return dataPlaneRegistry.getUserRepository(domain).findByIdIn(Reference.domain(domain.getId()), pagedMemberIds).toList().map(users -> new Page<>(users, page, sortedMembers.size()));
                    }
                });
    }

    @Override
    public Flowable<Group> findByIdIn(Domain domain, List<String> ids) {
        LOGGER.debug("Find groups for ids : {}", ids);
        return dataPlaneRegistry.getGroupRepository(domain)
                .findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using ids {}", ids, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a group using ids: %s", ids), ex));
                });
    }

    @Override
    public Single<Group> create(Domain domain, NewGroup newGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Create a new group {} for domain {}", newGroup.getName(), domain.getId());

        return findByName(domain, newGroup.getName())
                .isEmpty()
                .map(isEmpty -> {
                    if (isEmpty) {
                        String groupId = RandomString.generate();
                        Group group = new Group();
                        group.setId(groupId);
                        group.setReferenceType(ReferenceType.DOMAIN);
                        group.setReferenceId(domain.getId());
                        group.setName(newGroup.getName());
                        group.setDescription(newGroup.getDescription());
                        group.setMembers(newGroup.getMembers());
                        group.setCreatedAt(new Date());
                        group.setUpdatedAt(group.getCreatedAt());
                        return group;
                    } else {
                        throw new GroupAlreadyExistsException(newGroup.getName());
                    }
                })
                .flatMap(grp -> this.setMembers(domain, grp))
                .flatMap(group -> dataPlaneRegistry.getGroupRepository(domain).create(group))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to create a group", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to create a group", ex));
                    }
                })
                .doOnSuccess(group -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).group(group)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).reference(Reference.domain(domain.getId())).throwable(throwable)));
    }

    @Override
    public Single<Group> update(Domain domain, String id, UpdateGroup updateGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Update a group {} for domain {}", id, domain.getId());

        return findById(domain, id)
                // check uniqueness
                .flatMap(existingGroup -> dataPlaneRegistry.getGroupRepository(domain).findByName(ReferenceType.DOMAIN, domain.getId(), updateGroup.getName())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .map(optionalGroup -> {
                            if (optionalGroup.isPresent() && !id.equals(optionalGroup.get().getId())) {
                                throw new GroupAlreadyExistsException(updateGroup.getName());
                            }
                            return existingGroup;
                        })
                )
                .flatMap(oldGroup -> {
                    Group groupToUpdate = new Group(oldGroup);
                    groupToUpdate.setName(updateGroup.getName());
                    groupToUpdate.setDescription(updateGroup.getDescription());
                    groupToUpdate.setMembers(updateGroup.getMembers());
                    groupToUpdate.setUpdatedAt(new Date());

                    // set members and update
                    return setMembers(domain, groupToUpdate)
                            .flatMap(group -> dataPlaneRegistry.getGroupRepository(domain).update(group))
                            .doOnSuccess(group -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).oldValue(oldGroup).group(group)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).reference(Reference.domain(domain.getId())).throwable(throwable)));

                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a group", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a group", ex));
                });
    }

    @Override
    public Completable delete(Domain domain, String groupId, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Delete group {}", groupId);

        return findById(domain, groupId)
                .flatMapCompletable(group -> dataPlaneRegistry.getGroupRepository(domain).delete(groupId)
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_DELETED).group(group)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_DELETED).group(group).throwable(throwable)))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete group: {}", groupId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete group: %s", groupId), ex));
                });
    }

    @Override
    public Single<Group> assignRoles(Domain domain, String groupId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(domain, groupId, roles, principal, false);
    }

    @Override
    public Single<Group> revokeRoles(Domain domain, String groupId, List<String> roles, io.gravitee.am.identityprovider.api.User principal) {
        return assignRoles0(domain, groupId, roles, principal, true);
    }

    private Single<Group> assignRoles0(Domain domain, String groupId, List<String> roles, io.gravitee.am.identityprovider.api.User principal, boolean revoke) {
        return findById(domain, groupId)
                .flatMap(oldGroup -> {
                    Group groupToUpdate = new Group(oldGroup);
                    // remove existing roles from the group
                    if (revoke) {
                        if (groupToUpdate.getRoles() != null) {
                            groupToUpdate.getRoles().removeAll(roles);
                        }
                    } else {
                        groupToUpdate.setRoles(roles);
                    }
                    // check roles
                    return checkRoles(roles)
                            // and update the group
                            .andThen(Single.defer(() -> dataPlaneRegistry.getGroupRepository(domain).update(groupToUpdate)))
                            .doOnSuccess(group -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_ROLES_ASSIGNED).oldValue(oldGroup).group(group)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_ROLES_ASSIGNED).reference(Reference.domain(domain.getId())).throwable(throwable)));
                });
    }

    private Single<Group> setMembers(Domain domain, Group group) {
        List<String> userMembers = group.getMembers() != null ? group.getMembers().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList()) : null;
        if (userMembers != null && !userMembers.isEmpty()) {
            return dataPlaneRegistry.getUserRepository(domain).findByIdIn(Reference.domain(domain.getId()), userMembers)
                    .map(User::getId)
                    .toList()
                    .map(userIds -> {
                        group.setMembers(userIds);
                        return group;
                    });
        }
        return Single.just(group);
    }

    private Completable checkRoles(List<String> roles) {
        return Completable.fromSingle(roleService.findByIdIn(roles)
                .map(roles1 -> {
                    if (roles1.size() != roles.size()) {
                        // find difference between the two list
                        roles.removeAll(roles1.stream().map(Role::getId).collect(Collectors.toList()));
                        throw new RoleNotFoundException(String.join(",", roles));
                    }
                    return roles1;
                }));
    }
}
