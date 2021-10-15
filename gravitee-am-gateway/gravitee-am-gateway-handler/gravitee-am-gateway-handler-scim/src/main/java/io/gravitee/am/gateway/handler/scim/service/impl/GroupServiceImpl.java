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
package io.gravitee.am.gateway.handler.scim.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.scim.exception.SCIMException;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.*;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.GroupAuditBuilder;
import io.reactivex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupServiceImpl implements GroupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupServiceImpl.class);

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Domain domain;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<ListResponse<Group>> list(int page, int size, String baseUrl) {
        LOGGER.debug("Find groups by domain : {}", domain.getId());

        return groupRepository.findAll(ReferenceType.DOMAIN, domain.getId(), page, size)
                .flatMap(groupPage -> {
                    // A negative value SHALL be interpreted as "0".
                    // A value of "0" indicates that no resource results are to be returned except for "totalResults".
                    if (size <= 0) {
                        return Single.just(new ListResponse<Group>(null, groupPage.getCurrentPage() + 1, groupPage.getTotalCount(), 0));
                    } else {
                        // SCIM use 1-based index (increment current page)
                        return Observable.fromIterable(groupPage.getData())
                                .map(group -> convert(group, baseUrl, true))
                                // set members
                                .flatMapSingle(group -> setMembers(group, baseUrl))
                                .toList()
                                .map(groups -> new ListResponse<>(groups, groupPage.getCurrentPage() + 1, groupPage.getTotalCount(), groups.size()));
                    }
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find groups by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find groups by domain %s", domain), ex));
                });
    }

    @Override
    public Flowable<Group> findByMember(String memberId) {
        LOGGER.debug("Find groups by member : {}", memberId);
        return groupRepository.findByMember(memberId)
                .map(group -> convert(group, null, true))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a groups using member ", memberId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using member: %s", memberId), ex));
                });
    }

    @Override
    public Maybe<Group> get(String groupId, String baseUrl) {
        LOGGER.debug("Find group by id : {}", groupId);
        return groupRepository.findById(groupId)
                .map(group -> convert(group, baseUrl, false))
                // set members
                .flatMap(group -> setMembers(group, baseUrl).toMaybe())
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a group using its ID", groupId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", groupId), ex));
                });
    }

    @Override
    public Single<Group> create(Group group, String baseUrl, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Create a new group {} for domain {}", group.getDisplayName(), domain.getName());

        io.gravitee.am.model.Group groupModel = convert(group);
        // set technical ID
        groupModel.setId(RandomString.generate());
        groupModel.setReferenceType(ReferenceType.DOMAIN);
        groupModel.setReferenceId(domain.getId());
        groupModel.setCreatedAt(new Date());
        groupModel.setUpdatedAt(groupModel.getCreatedAt());

        // check if user is unique
        return groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), group.getDisplayName())
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new UniquenessException("Group with display name [" + group.getDisplayName()+ "] already exists");
                    }
                    return true;
                })
                // set members
                .flatMap(__ -> setMembers(group, baseUrl))
                .flatMap(group1 -> {
                    return groupRepository.create(groupModel);
                })
                .doOnSuccess(grp -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).group(grp)))
                .map(group1 -> convert(group1, baseUrl, true))
                // set members
                .flatMap(group1 -> setMembers(group1, baseUrl))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof SCIMException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to router a group", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to router a group", ex));
                    }
                })
                .doOnError(error -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_CREATED).group(groupModel).throwable(error)));
    }

    @Override
    public Single<Group> update(String groupId, Group group, String baseUrl, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Update a group {} for domain {}", groupId, domain.getName());
        return groupRepository.findById(groupId)
                .switchIfEmpty(Maybe.error(new GroupNotFoundException(groupId)))
                .flatMapSingle(existingGroup -> groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), group.getDisplayName())
                        .map(group1 -> {
                            // if display name has changed check uniqueness
                            if (!existingGroup.getId().equals(group1.getId())) {
                                throw new UniquenessException("Group with display name [" + group.getDisplayName()+ "] already exists");
                            }
                            return existingGroup;
                        })
                        .defaultIfEmpty(existingGroup)
                        // set members
                        .flatMapSingle(irrelevant -> setMembers(group, baseUrl))
                        .flatMap(group1 -> {
                            io.gravitee.am.model.Group groupToUpdate = convert(group1);
                            // set immutable attribute
                            groupToUpdate.setId(existingGroup.getId());
                            groupToUpdate.setReferenceType(existingGroup.getReferenceType());
                            groupToUpdate.setReferenceId(existingGroup.getReferenceId());
                            groupToUpdate.setCreatedAt(existingGroup.getCreatedAt());
                            groupToUpdate.setUpdatedAt(new Date());
                            return groupRepository.update(groupToUpdate);
                        })
                        .doOnSuccess(grp -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).oldValue(existingGroup).group(grp)))
                        .doOnError(error -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_UPDATED).group(existingGroup).throwable(error))))
                .map(group1 -> convert(group1, baseUrl, false))
                // set members
                .flatMap(group1 -> setMembers(group1, baseUrl))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof SCIMException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to update a group", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to update a group", ex));
                    }
                });
    }

    @Override
    public Single<Group> patch(String groupId, PatchOp patchOp, String baseUrl, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Patch a group {} for domain {}", groupId, domain.getName());
        return get(groupId, baseUrl)
                .switchIfEmpty(Single.error(new GroupNotFoundException(groupId)))
                .flatMap(group -> {
                    ObjectNode node = objectMapper.convertValue(group, ObjectNode.class);
                    patchOp.getOperations().forEach(operation -> operation.apply(node));
                    return update(groupId, objectMapper.treeToValue(node, Group.class), baseUrl, principal);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error has occurred when trying to delete group: {}", groupId, ex);
                        return Single.error(new TechnicalManagementException(
                                String.format("An error has occurred when trying to delete group: %s", groupId), ex));
                    }
                });
    }

    @Override
    public Completable delete(String groupId, io.gravitee.am.identityprovider.api.User principal) {
        LOGGER.debug("Delete group {}", groupId);
        return groupRepository.findById(groupId)
                .switchIfEmpty(Maybe.error(new GroupNotFoundException(groupId)))
                .flatMapCompletable(group -> groupRepository.delete(groupId)
                        .andThen(Completable.fromAction(() -> auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_DELETED).group(group)))))
                .doOnError(error -> {
                    io.gravitee.am.model.Group group = new io.gravitee.am.model.Group();
                    group.setId(groupId);
                    group.setReferenceId(domain.getId());
                    group.setReferenceType(ReferenceType.DOMAIN);
                    auditService.report(AuditBuilder.builder(GroupAuditBuilder.class).principal(principal).type(EventType.GROUP_DELETED).group(group).throwable(error));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to delete group: {}", groupId, ex);
                        return Completable.error(new TechnicalManagementException(
                                String.format("An error occurs while trying to delete group: %s", groupId), ex));
                    }
                });
    }

    private Single<Group> setMembers(Group group, String baseUrl) {
        Set<Member> members = group.getMembers() != null ? new HashSet<>(group.getMembers()) : null;
        if (members != null && !members.isEmpty()) {
            List<String> memberIds = group.getMembers().stream().map(Member::getValue).collect(Collectors.toList());
            return userRepository.findByIdIn(memberIds)
                    .map(user -> {
                        String display = (user.getDisplayName() != null) ? user.getDisplayName()
                                : (user.getFirstName() != null) ? user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")
                                : user.getUsername();
                        String usersBaseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/Groups")).concat("/Users");
                        Member member = new Member();
                        member.setValue(user.getId());
                        member.setDisplay(display);
                        member.setRef(usersBaseUrl + "/" + user.getId());
                        return member;
                    })
                    .toList()
                    .map(existingMembers -> {
                        group.setMembers(existingMembers);
                        return group;
                    });
        } else {
            return Single.just(group);
        }
    }

    private Group convert(io.gravitee.am.model.Group group, String baseUrl, boolean listing) {
        Group scimGroup = new Group();
        scimGroup.setSchemas(Group.SCHEMAS);
        scimGroup.setId(group.getId());
        scimGroup.setDisplayName(group.getName());

        // members
        if (group.getMembers() != null && !group.getMembers().isEmpty()) {
            scimGroup.setMembers(group.getMembers().stream().map(userId -> {
                Member member = new Member();
                member.setValue(userId);
                return member;
            }).collect(Collectors.toList()));
        }

        // Meta
        Meta meta = new Meta();
        if (group.getCreatedAt() != null) {
            meta.setCreated(group.getCreatedAt().toInstant().toString());
        }
        if (group.getUpdatedAt() != null) {
            meta.setLastModified(group.getUpdatedAt().toInstant().toString());
        }
        meta.setResourceType(Group.RESOURCE_TYPE);
        if (baseUrl != null) {
            meta.setLocation(baseUrl + (listing ?  "/" + scimGroup.getId() : ""));
        }
        scimGroup.setMeta(meta);

        return scimGroup;
    }

    private io.gravitee.am.model.Group convert(Group scimGroup) {
        io.gravitee.am.model.Group group = new io.gravitee.am.model.Group();
        group.setId(scimGroup.getId());
        group.setName(scimGroup.getDisplayName());

        if (scimGroup.getMembers() != null) {
            group.setMembers(scimGroup.getMembers().stream().map(Member::getValue).collect(Collectors.toList()));
        }
        return group;
    }
}
