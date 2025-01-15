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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.GroupAlreadyExistsException;
import io.gravitee.am.service.exception.GroupNotFoundException;
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
public class OrganizationGroupServiceImpl implements OrganizationGroupService {

    private final Logger LOGGER = LoggerFactory.getLogger(OrganizationGroupServiceImpl.class);

    @Lazy
    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private OrganizationUserService organizationUserService;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<Page<Group>> findAll(String organizationId, int page, int size) {
        LOGGER.debug("Find groups by {}: {}", ReferenceType.ORGANIZATION, organizationId);
        return groupRepository.findAll(ReferenceType.ORGANIZATION, organizationId, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups by {} {}", ReferenceType.ORGANIZATION, organizationId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by %s %s", ReferenceType.ORGANIZATION, organizationId), ex));
                });
    }

    @Override
    public Flowable<Group> findAll(String organizationId) {
        LOGGER.debug("Find groups by {}: {}", ReferenceType.ORGANIZATION, organizationId);
        return groupRepository.findAll(ReferenceType.ORGANIZATION, organizationId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups by {} {}", ReferenceType.ORGANIZATION, organizationId, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by %s %s", ReferenceType.ORGANIZATION, organizationId), ex));
                });
    }

    public Maybe<Group> findByName(String organizationId, String groupName) {
        LOGGER.debug("Find group by {} and name: {} {}", ReferenceType.ORGANIZATION, organizationId, groupName);
        return groupRepository.findByName(ReferenceType.ORGANIZATION, organizationId, groupName)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its name: {} for the {} {}", groupName, ReferenceType.ORGANIZATION, organizationId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its name: %s for the %s %s", groupName, ReferenceType.ORGANIZATION, organizationId), ex));
                });
    }

    @Override
    public Flowable<Group> findByMember(String memberId) {
        LOGGER.debug("Find groups by member : {}", memberId);
        return groupRepository.findByMember(memberId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a groups using member {} ", memberId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using member: %s", memberId), ex));
                });
    }

    @Override
    public Single<Group> findById(String organizationId, String id) {
        LOGGER.debug("Find group by id : {}", id);
        return groupRepository.findById(ReferenceType.ORGANIZATION, organizationId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its id {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a group using its id: %s", id), ex));
                })
                .switchIfEmpty(Single.error(new GroupNotFoundException(id)));
    }

    @Override
    public Maybe<Group> findById(String id) {
        LOGGER.debug("Find group by id : {}", id);
        return groupRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its ID {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a group using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<Page<User>> findMembers(String organizationId, String groupId, int page, int size) {
        LOGGER.debug("Find members for group : {}", groupId);
        return findById(organizationId, groupId)
                .flatMap(group -> {
                    if (group.getMembers() == null || group.getMembers().isEmpty()) {
                        return Single.just(new Page<>(null, page, 0));
                    } else {
                        // get members
                        List<String> sortedMembers = group.getMembers().stream().sorted().collect(Collectors.toList());
                        final int startOffset = page * size;
                        final int endOffset = (page + 1) * size;
                        List<String> pagedMemberIds = sortedMembers.subList(Math.min(sortedMembers.size(), startOffset), Math.min(sortedMembers.size(), endOffset));
                        return organizationUserService.findByIdIn(pagedMemberIds).toList().map(users -> new Page<>(users, page, sortedMembers.size()));
                    }
                });
    }

    @Override
    public Flowable<Group> findByIdIn(List<String> ids) {
        LOGGER.debug("Find groups for ids : {}", ids);
        return groupRepository.findByIdIn(ids)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using ids {}", ids, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a group using ids: %s", ids), ex));
                });
    }

    @Override
    public Single<Group> create(String organizationId, NewGroup newGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Create a new group {} for {} {}", newGroup.getName(), ReferenceType.ORGANIZATION, organizationId);

        return findByName(organizationId, newGroup.getName())
                .isEmpty()
                .map(isEmpty -> {
                    if (isEmpty) {
                        String groupId = RandomString.generate();
                        Group group = new Group();
                        group.setId(groupId);
                        group.setReferenceType(ReferenceType.ORGANIZATION);
                        group.setReferenceId(organizationId);
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
                .flatMap(this::setMembers)
                .flatMap(group -> groupRepository.create(group))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to create a group", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to create a group", ex));
                    }
                })
                .doOnSuccess(group -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).group(group)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).reference(new Reference(ReferenceType.ORGANIZATION, organizationId)).throwable(throwable)));
    }

    @Override
    public Single<Group> update(String organizationId, String id, UpdateGroup updateGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Update a group {} for {} {}", id, ReferenceType.ORGANIZATION, organizationId);

        return findById(organizationId, id)
                // check uniqueness
                .flatMap(existingGroup -> groupRepository.findByName(ReferenceType.ORGANIZATION, organizationId, updateGroup.getName())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .map(optionalGroup -> {
                            if (optionalGroup.isPresent() && !optionalGroup.get().getId().equals(id)) {
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
                    return setMembers(groupToUpdate)
                            .flatMap(group -> groupRepository.update(group))
                            .doOnSuccess(group -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).oldValue(oldGroup).group(group)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).reference(new Reference(ReferenceType.ORGANIZATION, organizationId)).throwable(throwable)));

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
    public Completable delete(String organizationId, String groupId, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Delete group {}", groupId);

        return findById(organizationId, groupId)
                .flatMapCompletable(group -> groupRepository.delete(groupId)
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

    private Single<Group> setMembers(Group group) {
        List<String> userMembers = group.getMembers() != null ? group.getMembers().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList()) : null;
        if (userMembers != null && !userMembers.isEmpty()) {
            return organizationUserService.findByIdIn(userMembers)
                    .map(User::getId)
                    .toList()
                    .map(userIds -> {
                        group.setMembers(userIds);
                        return group;
                    });
        }
        return Single.just(group);
    }
}
