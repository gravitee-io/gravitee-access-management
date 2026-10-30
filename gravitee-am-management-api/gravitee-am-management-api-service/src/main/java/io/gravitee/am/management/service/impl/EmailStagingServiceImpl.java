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

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.management.service.ActionLeaseService;
import io.gravitee.am.management.service.EmailStagingService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.common.EmailStagingRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@CustomLog
public class EmailStagingServiceImpl implements EmailStagingService {

    public static final String ACTION_EMAIL_STAGING_PROCESS = "process_email_staging";
    public static final int DEFAULT_LEASE_DURATION_IN_SECONDS = 600;

    private final EmailStagingRepository emailStagingRepository;

    private final ActionLeaseService actionLeaseService;

    private final Duration leaseDuration;

    public EmailStagingServiceImpl(@Lazy @Qualifier("managementEmailStagingRepository") EmailStagingRepository emailStagingRepository,
                                   @Lazy ActionLeaseService actionLeaseService,
                                   @Value("${email.bulk.leaseDuration:" + DEFAULT_LEASE_DURATION_IN_SECONDS + "}") int leaseDurationInSeconds) {
        this.emailStagingRepository = emailStagingRepository;
        this.actionLeaseService = actionLeaseService;
        this.leaseDuration = Duration.ofSeconds(leaseDurationInSeconds);
    }

    @Override
    public Completable push(User user, Application client, Template template) {
        var staging = new EmailStaging();
        staging.setUserId(user.getId());
        staging.setReferenceType(ReferenceType.DOMAIN);
        staging.setReferenceId(user.getReferenceId());
        staging.setEmailTemplateName(template.name());
        staging.setAttempts(0);
        if (client != null) {
            staging.setApplicationId(client.getId());
        }
        return emailStagingRepository.create(staging)
                .doOnSuccess(entity -> log.debug("Email for user {} staged with id {}", user.getId(), entity.getId()))
                .ignoreElement();
    }

    @Override
    public Flowable<Reference> listReferences() {
        return emailStagingRepository.listReferences();
    }

    @Override
    public Flowable<EmailStaging> acquireLeaseAndFetch(Reference reference, int batchSize) {
        return actionLeaseService.acquireLease(actionId(reference), leaseDuration)
                .switchIfEmpty(Maybe.error(() -> new ActionLeaseException("Unable to acquire action lease for " + actionId(reference), leaseDuration)))
                .flatMapPublisher(lease -> emailStagingRepository.findOldestByUpdateDate(reference, batchSize));
    }

    @Override
    public Single<EmailStaging> manageAfterProcessing(EmailStaging emailStaging) {
        var result = emailStaging.isProcessed()
                ? emailStagingRepository.delete(emailStaging.getId()).andThen(Single.just(emailStaging))
                : emailStagingRepository.updateAttempts(emailStaging.getId(), emailStaging.getAttempts());
        return result.onErrorResumeNext(error -> {
            log.error("An error occurs while processing email staging {}", emailStaging, error);
            return Single.just(emailStaging);
        });
    }

    @Override
    public Completable releaseLease(Reference reference) {
        return actionLeaseService.releaseLease(actionId(reference))
                .doOnError(error -> log.warn("Failed to release action lease for {}", actionId(reference), error))
                .onErrorComplete();
    }

    private static String actionId(Reference reference) {
        return ACTION_EMAIL_STAGING_PROCESS + ":" + reference.id();
    }
}
