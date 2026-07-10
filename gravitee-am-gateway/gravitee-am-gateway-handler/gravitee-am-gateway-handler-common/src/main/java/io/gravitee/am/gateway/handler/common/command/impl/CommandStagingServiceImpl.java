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
package io.gravitee.am.gateway.handler.common.command.impl;

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.gateway.handler.common.command.CommandStagingService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.command.CommandRequest;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.gateway.api.CommandStagingRepository;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * @author GraviteeSource Team
 */
@Slf4j
public class CommandStagingServiceImpl implements CommandStagingService, InitializingBean {

    public static final String ACTION_COMMAND_STAGING_PROCESS = "process_command_staging";
    private static final int DEFAULT_COMMAND_LEASE_DURATION_IN_SECOND = 60;

    @Autowired
    private Domain domain;

    @Autowired
    private CommandStagingRepository commandStagingRepository;

    @Autowired
    @Qualifier("gatewayActionLeaseRepository")
    private ActionLeaseRepository actionLeaseRepository;

    @Autowired
    private Node node;

    @Value("${commands.dispatch.leaseDuration:" + DEFAULT_COMMAND_LEASE_DURATION_IN_SECOND + "}")
    private int leaseDuration = DEFAULT_COMMAND_LEASE_DURATION_IN_SECOND;

    private String workerId;
    private String actionId;

    @Override
    public void afterPropertiesSet() throws Exception {
        // the actionId includes the domainId so each domain has its own lease,
        // which also covers MultiDataPlane deployments
        this.workerId = node.id();
        this.actionId = ACTION_COMMAND_STAGING_PROCESS + ":" + domain.getId();
    }

    @Override
    public Completable stage(CommandRequest commandRequest) {
        final var staging = new CommandStaging();
        staging.setId(commandRequest.getId());
        staging.setCommand(commandRequest.getCommand());
        staging.setUserId(commandRequest.getUserId());
        staging.setReferenceType(ReferenceType.DOMAIN);
        staging.setReferenceId(domain.getId());
        staging.setAttempts(0);

        return commandStagingRepository.createIfAbsent(staging)
                .doOnSuccess(entity -> log.debug("Command {} persisted in staging state for domain {}", entity.getId(), domain.getName()))
                .ignoreElement();
    }

    @Override
    public Flowable<CommandStaging> acquireLeaseAndFetch(Reference reference, int batchSize) {
        Duration duration = Duration.of(leaseDuration, ChronoUnit.SECONDS);
        return actionLeaseRepository.acquireLease(this.actionId, this.workerId, duration)
                .switchIfEmpty(Maybe.error(() -> new ActionLeaseException("Unable to acquire action lease for " + ACTION_COMMAND_STAGING_PROCESS + " action", duration)))
                .flatMapPublisher(lease -> commandStagingRepository.findOldestByUpdateDate(reference, batchSize));
    }

    @Override
    public Single<CommandStaging> manageAfterProcessing(CommandStaging commandStaging) {
        Single<CommandStaging> single;
        if (commandStaging.isProcessed()) {
            single = commandStagingRepository.delete(commandStaging.getId()).andThen(Single.just(commandStaging));
        } else {
            single = commandStagingRepository.update(commandStaging);
        }

        return single.onErrorResumeNext(error -> {
            log.error("An error occurs while processing command staging {}", commandStaging, error);
            return Single.just(commandStaging);
        });
    }

    @Override
    public Completable releaseLease(Reference reference) {
        return actionLeaseRepository.releaseLease(this.actionId, this.workerId)
                .doOnError(error -> log.warn("Failed to release action lease for {}", ACTION_COMMAND_STAGING_PROCESS, error))
                .onErrorComplete(); // Don't fail the stream if release fails
    }
}
