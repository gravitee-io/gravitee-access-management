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
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Tag;
import io.gravitee.am.repository.management.api.TagRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.TagService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TagAlreadyExistsException;
import io.gravitee.am.service.exception.TagNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewTag;
import io.gravitee.am.service.model.UpdateTag;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.TagAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class TagServiceImpl implements TagService {

    private final Logger LOGGER = LoggerFactory.getLogger(TagServiceImpl.class);

    @Lazy
    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private DomainService domainService;

    @Override
    public Maybe<Tag> findById(String id, String organizationId) {
        LOGGER.debug("Find tag by ID: {}", id);
        return tagRepository.findById(id, organizationId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a tag using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a tag using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<Tag> findAll(String organizationId) {
        LOGGER.debug("Find all tags");
        return tagRepository.findAll(organizationId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all tags", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find all tags", ex));
                });
    }

    @Override
    public Single<Tag> create(NewTag newTag, String organizationId, User principal) {
        LOGGER.debug("Create a new tag: {}", newTag);
        String id = humanReadableId(newTag.getName());

        return tagRepository.findById(id, organizationId)
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new TagAlreadyExistsException(newTag.getName());
                    } else {
                        Tag tag = new Tag();
                        tag.setId(id);
                        tag.setOrganizationId(organizationId);
                        tag.setName(newTag.getName());
                        tag.setDescription(newTag.getDescription());
                        tag.setCreatedAt(new Date());
                        tag.setUpdatedAt(tag.getCreatedAt());
                        return tagRepository.create(tag);
                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a tag", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a tag", ex));
                })
                .doOnSuccess(tag -> auditService.report(AuditBuilder.builder(TagAuditBuilder.class).tag(tag).principal(principal).type(EventType.TAG_CREATED)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(TagAuditBuilder.class).reference(Reference.organization(organizationId)).principal(principal).type(EventType.TAG_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Tag> update(String tagId, String organizationId, UpdateTag updateTag, User principal) {
        LOGGER.debug("Update an existing tag: {}", updateTag);
        return tagRepository.findById(tagId, organizationId)
                .switchIfEmpty(Single.error(new TagNotFoundException(tagId)))
                .flatMap(oldTag -> {
                    Tag tag = new Tag();
                    tag.setId(tagId);
                    tag.setName(updateTag.getName());
                    tag.setOrganizationId(organizationId);
                    tag.setDescription(updateTag.getDescription());
                    tag.setCreatedAt(oldTag.getCreatedAt());
                    tag.setUpdatedAt(new Date());

                    return tagRepository.update(tag)
                            .doOnSuccess(tag1 -> auditService.report(AuditBuilder.builder(TagAuditBuilder.class).principal(principal).type(EventType.TAG_UPDATED).tag(tag1).oldValue(oldTag)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(TagAuditBuilder.class).principal(principal).type(EventType.TAG_UPDATED).reference(Reference.organization(organizationId)).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a tag", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a tag", ex));
                });
    }

    @Override
    public Completable delete(String tagId, String organizationId, User principal) {
        LOGGER.debug("Delete tag {}", tagId);
        return tagRepository.findById(tagId, organizationId)
                .switchIfEmpty(Maybe.error(new TagNotFoundException(tagId)))
                .flatMapCompletable(tag -> removeTagsFromDomains(tagId)
                        .andThen(tagRepository.delete(tagId))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(TagAuditBuilder.class).principal(principal).type(EventType.TAG_DELETED).tag(tag)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(TagAuditBuilder.class).principal(principal).type(EventType.TAG_DELETED).reference(Reference.organization(organizationId)).throwable(throwable))))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete tag {}", tagId, ex);
                    return Completable.error(new TechnicalManagementException("An error occurs while trying to delete tag " + tagId, ex));
                });
    }

    private Completable removeTagsFromDomains(String tagId){
        // todo mre listAll() -> findByTag(tagId)
        return domainService.listAll()
                .filter(domain -> domain.getTags() != null && domain.getTags().contains(tagId))
                .flatMapCompletable(domain -> {
                    domain.getTags().remove(tagId);
                    return Completable.fromSingle(domainService.update(domain.getId(), domain)
                            .doOnSuccess(d -> LOGGER.info("Removed tag {} from domain {}", tagId, domain.getName()))
                            .doOnError(ex -> LOGGER.error("An error occurs while trying to delete tag {} from domain {}", tagId, domain.getName())));
                });
    }

    private String humanReadableId(String domainName) {
        String nfdNormalizedString = Normalizer.normalize(domainName, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        domainName = pattern.matcher(nfdNormalizedString).replaceAll("");
        return domainName.toLowerCase().trim().replaceAll("\\s{1,}", "-");
    }
}
