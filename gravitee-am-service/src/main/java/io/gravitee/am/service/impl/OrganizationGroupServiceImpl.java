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
    public Single<Page<Group>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("Find groups by {}: {}", referenceType, referenceId);
        return groupRepository.findAll(referenceType, referenceId, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups by {} {}", referenceType, referenceId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by %s %s", referenceType, referenceId), ex));
                });
    }

    @Override
    public Single<Page<Group>> findByDomain(String domain, int page, int size) {
        return findAll(ReferenceType.DOMAIN, domain, page, size);
    }

    @Override
    public Flowable<Group> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("Find groups by {}: {}", referenceType, referenceId);
        return groupRepository.findAll(referenceType, referenceId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups by {} {}", referenceType, referenceId, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by %s %s", referenceType, referenceId), ex));
                });
    }

    @Override
    public Flowable<Group> findByDomain(String domain) {
        return findAll(ReferenceType.DOMAIN, domain);
    }


    public Maybe<Group> findByName(ReferenceType referenceType, String referenceId, String groupName) {
        LOGGER.debug("Find group by {} and name: {} {}", referenceType, referenceId, groupName);
        return groupRepository.findByName(referenceType, referenceId, groupName)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its name: {} for the {} {}", groupName, referenceType, referenceId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its name: %s for the %s %s", groupName, referenceType, referenceId), ex));
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
    public Single<Group> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("Find group by id : {}", id);
        return groupRepository.findById(referenceType, referenceId, id)
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
    public Single<Page<User>> findMembers(ReferenceType referenceType, String referenceId, String groupId, int page, int size) {
        LOGGER.debug("Find members for group : {}", groupId);
        return findById(referenceType, referenceId, groupId)
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
    public Single<Group> create(ReferenceType referenceType, String referenceId, NewGroup newGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Create a new group {} for {} {}", newGroup.getName(), referenceType, referenceId);

        return findByName(referenceType, referenceId, newGroup.getName())
                .isEmpty()
                .map(isEmpty -> {
                    if (isEmpty) {
                        String groupId = RandomString.generate();
                        Group group = new Group();
                        group.setId(groupId);
                        group.setReferenceType(referenceType);
                        group.setReferenceId(referenceId);
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
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).reference(new Reference(referenceType, referenceId)).throwable(throwable)));
    }

    @Override
    public Single<Group> update(ReferenceType referenceType, String referenceId, String id, UpdateGroup updateGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Update a group {} for {} {}", id, referenceType, referenceId);

        return findById(referenceType, referenceId, id)
                // check uniqueness
                .flatMap(existingGroup -> groupRepository.findByName(referenceType, referenceId, updateGroup.getName())
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
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).reference(new Reference(referenceType, referenceId)).throwable(throwable)));

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
    public Single<Group> update(String domain, String id, UpdateGroup updateGroup, io.gravitee.am.identityprovider.api.User principal) {
        return update(ReferenceType.DOMAIN, domain, id, updateGroup, principal);
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String groupId, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Delete group {}", groupId);

        return findById(referenceType, referenceId, groupId)
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
