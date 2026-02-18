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
package io.gravitee.am.gateway.handler.common.email.impl;

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.gateway.handler.common.email.EmailContainer;
import io.gravitee.am.gateway.handler.common.email.EmailStagingService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.gateway.api.EmailStagingRepository;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class EmailStagingServiceImpl implements EmailStagingService, InitializingBean {

    public static final String ACTION_EMAIL_STAGING_PROCESS = "process_email_staging";
    private static final int DEFAULT_EMAIL_LEASE_DURATION_IN_SECOND = 600;

    @Autowired
    private Domain domain;

    @Autowired
    private EmailStagingRepository emailStagingRepository;

    @Autowired
    private ActionLeaseRepository actionLeaseRepository;

    @Autowired
    private Node node;

    @Value("${email.bulk.leaseDuration:"+ DEFAULT_EMAIL_LEASE_DURATION_IN_SECOND +"}")
    private int leaseDuration = DEFAULT_EMAIL_LEASE_DURATION_IN_SECOND;

    private String workerId;
    private String actionId;

    @Override
    public void afterPropertiesSet() throws Exception {
        // create an actionId with the nodeId and the domainId
        // so each GW as a chance to manage some email
        // and is also manage the case of MultiDataPlane deployment
        this.workerId = node.id();
        this.actionId = ACTION_EMAIL_STAGING_PROCESS + ":" + domain.getId();
    }

    @Override
    public Completable push(EmailContainer emailContainer, Template template) {
        // no need to be a leader here, we can push without action lease
        final var staging = new EmailStaging();
        staging.setUserId(emailContainer.user().getId());
        staging.setReferenceId(domain.getId());
        staging.setReferenceType(ReferenceType.DOMAIN);
        staging.setEmailTemplateName(template.name());
        staging.setAttempts(0);
        Optional.ofNullable(emailContainer.client()).ifPresent(emailClient -> staging.setApplicationId(emailClient.getId()));

        return emailStagingRepository.create(staging)
                .doOnSuccess( entity -> {
                    log.debug("Email {} persisted in staging state with id {}", emailContainer.user().getEmail(), entity.getId());
                })
                .ignoreElement();
    }

    @Override
    public Flowable<EmailStaging> acquireLeaseAndFetch(Reference reference, int batchSize) {
        return actionLeaseRepository.acquireLease(this.actionId, this.workerId, Duration.of(leaseDuration, ChronoUnit.SECONDS))
                .switchIfEmpty(Maybe.error(() -> new ActionLeaseException("Unable to acquire action lease for " + ACTION_EMAIL_STAGING_PROCESS + " action")))
                .flatMapPublisher(lease ->
                        emailStagingRepository.findOldestByUpdateDate(reference, batchSize)
                );
    }

    @Override
    public Single<EmailStaging> manageAfterProcessing(EmailStaging emailStaging) {
        Single<EmailStaging> single;
        if (emailStaging.isProcessed()) {
            single = emailStagingRepository.delete(emailStaging.getId()).andThen(Single.just(emailStaging));
        } else {
            single = emailStagingRepository.updateAttempts(emailStaging.getId(), emailStaging.getAttempts());
        }

        return single.onErrorResumeNext(error -> {
            log.error("An error occurs while processing email staging {}", emailStaging, error);
            return Single.just(emailStaging);
        });
    }

    @Override
    public Completable releaseLease(Reference reference) {
        return actionLeaseRepository.releaseLease(this.actionId, this.workerId)
                .doOnError(error -> log.warn("Failed to release action lease for {}", ACTION_EMAIL_STAGING_PROCESS, error))
                .onErrorComplete(); // Don't fail the stream if release fails
    }
}
