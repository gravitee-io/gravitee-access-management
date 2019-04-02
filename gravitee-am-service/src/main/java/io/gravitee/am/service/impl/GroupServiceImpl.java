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
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.GroupAlreadyExistsException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.am.service.model.UpdateGroup;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.GroupAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class GroupServiceImpl implements GroupService {

    private final Logger LOGGER = LoggerFactory.getLogger(GroupServiceImpl.class);

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditServiceImpl auditService;

    @Override
    public Single<Page<Group>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("Find groups by domain: {}", domain);
        return groupRepository.findByDomain(domain, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s", domain), ex));
                });
    }

    @Override
    public Single<List<Group>> findByDomain(String domain) {
        LOGGER.debug("Find groups by domain: {}", domain);
        return groupRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<User>> findMembers(String groupId, int page, int size) {
        LOGGER.debug("Find members for group : {}", groupId);
        return findById(groupId)
                .switchIfEmpty(Single.error(new GroupNotFoundException(groupId)))
                .flatMap(group -> {
                    if (group.getMembers() == null || group.getMembers().isEmpty()) {
                        return Single.just(new Page<>(null, page, size));
                    } else {
                        // get members
                        List<String> sortedMembers = group.getMembers().stream().sorted().collect(Collectors.toList());
                        List<String> pagedMemberIds = sortedMembers.subList(Math.min(sortedMembers.size(), page), Math.min(sortedMembers.size(), page + size));
                        return userService.findByIdIn(pagedMemberIds).map(users ->  new Page<>(users, page, pagedMemberIds.size()));
                    }
                });
    }

    @Override
    public Maybe<Group> findByDomainAndName(String domain, String groupName) {
        LOGGER.debug("Find group by domain and name: {} {}", domain, groupName);
        return groupRepository.findByDomainAndName(domain, groupName)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its name: {} for the domain {}", groupName, domain, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its name: %s for the domain %s", groupName, domain), ex));
                });
    }

    @Override
    public Maybe<Group> findById(String id) {
        LOGGER.debug("Find group by id : {}", id);
        return groupRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its ID", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a group using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<Group> create(String domain, NewGroup newGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Create a new group {} for domain {}", newGroup.getName(), domain);

        return findByDomainAndName(domain, newGroup.getName())
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new GroupAlreadyExistsException(newGroup.getName());
                    } else {
                        String groupId = RandomString.generate();
                        Group group = new Group();
                        group.setId(groupId);
                        group.setDomain(domain);
                        group.setName(newGroup.getName());
                        group.setMembers(newGroup.getMembers());
                        group.setCreatedAt(new Date());
                        group.setUpdatedAt(group.getCreatedAt());
                        return group;
                    }
                })
                .flatMap(group -> setMembers(group))
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
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).throwable(throwable)));

    }

    @Override
    public Single<Group> update(String domain, String id, UpdateGroup updateGroup, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Update a group {} for domain {}", id, domain);

        return groupRepository.findById(id)
                .switchIfEmpty(Maybe.error(new GroupNotFoundException(id)))
                // check uniqueness
                .flatMap(existingGroup -> groupRepository.findByDomainAndName(domain, updateGroup.getName())
                        .map(group -> Optional.of(group))
                        .defaultIfEmpty(Optional.empty())
                        .map(optionalGroup -> {
                            if (optionalGroup.isPresent() && !optionalGroup.get().getId().equals(id)) {
                                throw new GroupAlreadyExistsException(updateGroup.getName());
                            }
                            return existingGroup;
                        })
                )
                .flatMapSingle(oldGroup -> {
                    oldGroup.setName(updateGroup.getName());
                    oldGroup.setMembers(updateGroup.getMembers());
                    oldGroup.setUpdatedAt(new Date());

                    // set members and update
                    return setMembers(oldGroup)
                            .flatMap(group -> groupRepository.update(group))
                            .doOnSuccess(group -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).oldValue(oldGroup).group(group)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).throwable(throwable)));

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
    public Completable delete(String groupId, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Delete group {}", groupId);

        return groupRepository.findById(groupId)
                .switchIfEmpty(Maybe.error(new GroupNotFoundException(groupId)))
                .flatMapCompletable(group -> groupRepository.delete(groupId)
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_DELETED).group(group)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_DELETED).throwable(throwable)))
                )
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete group: {}", groupId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete user: %s", groupId), ex));
                });
    }

    private Single<Group> setMembers(Group group) {
        List<String> userMembers = group.getMembers() != null ? group.getMembers().stream().filter(member -> member != null).distinct().collect(Collectors.toList()) : null;
        if (userMembers != null) {
            return userService.findByIdIn(userMembers)
                    .map(users -> {
                        List<String> userIds = users.stream().map(User::getId).collect(Collectors.toList());
                        group.setMembers(userIds);
                        return group;
                    });
        }
        return Single.just(group);
    }
}
