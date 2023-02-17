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

package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.SystemTaskTypes;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class SystemTaskUpgrader implements Upgrader, Ordered {

    private final Logger logger = LoggerFactory.getLogger(SystemTaskUpgrader.class);

    @Lazy
    @Autowired
    protected SystemTaskRepository systemTaskRepository;

    @Override
    public boolean upgrade() {
        final String instanceOperationId = UUID.randomUUID().toString();
        final String taskId = getTaskId();
        boolean upgraded = systemTaskRepository.findById(taskId)
                .switchIfEmpty(Single.defer(() -> createSystemTask(instanceOperationId)))
                .flatMap(task -> {
                    switch (SystemTaskStatus.valueOf(task.getStatus())) {
                        case INITIALIZED:
                            return processUpgrade(instanceOperationId, task, instanceOperationId);
                        case FAILURE:
                            // In Failure case, we use the operationId of the read task otherwise update will always fail
                            // force the task.operationId to assign the task to the instance
                            String previousOperationId = task.getOperationId();
                            task.setOperationId(instanceOperationId);
                            return processUpgrade(instanceOperationId, task, previousOperationId);
                        case ONGOING:
                            // wait until status change
                            return Single.error(new IllegalStateException("ONGOING task " + taskId + " : trigger a retry"));
                        default:
                            // SUCCESS case
                            return Single.just(true);
                    }
                }).retryWhen(new RetryWithDelay(3, 5000)).blockingGet();

        if (!upgraded) {
            throw getIllegalStateException();
        }

        return true;
    }

    protected abstract Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String previousOperationId);

    protected abstract IllegalStateException getIllegalStateException();

    protected Single<SystemTask> createSystemTask(String operationId) {
        final String taskId = getTaskId();
        SystemTask systemTask = new SystemTask();
        systemTask.setId(taskId);
        systemTask.setType(SystemTaskTypes.UPGRADER.name());
        systemTask.setStatus(SystemTaskStatus.INITIALIZED.name());
        systemTask.setCreatedAt(new Date());
        systemTask.setUpdatedAt(systemTask.getCreatedAt());
        systemTask.setOperationId(operationId);
        return systemTaskRepository.create(systemTask).onErrorResumeNext(err -> {
            logger.warn("SystemTask {} can't be created due to '{}'", taskId, err.getMessage());
            // if the creation fails, try to find the task, this will allow to manage the retry properly
            return systemTaskRepository.findById(systemTask.getId()).toSingle();
        });
    }


    protected abstract String getTaskId();

    private static class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {
        private final int maxRetries;
        private final int retryDelayMillis;
        private int retryCount;

        public RetryWithDelay(int retries, int delay) {
            this.maxRetries = retries;
            this.retryDelayMillis = delay;
            this.retryCount = 0;
        }

        @Override
        public Publisher<?> apply(@NonNull Flowable<Throwable> attempts) throws Exception {
            return attempts
                    .flatMap((throwable) -> {
                        if (++retryCount < maxRetries) {
                            // When this Observable calls onNext, the original
                            // Observable will be retried (i.e. re-subscribed).
                            return Flowable.timer((long) retryDelayMillis * (retryCount + 1),
                                    TimeUnit.MILLISECONDS);
                        }
                        // Max retries hit. Just pass the error along.
                        return Flowable.error(throwable);
                    });
        }
    }
}
