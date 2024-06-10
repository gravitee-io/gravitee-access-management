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


import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.PasswordPolicyService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.DOMAIN_PASSWORD_POLICIES_UPGRADER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class DomainPasswordPoliciesUpgrader extends SystemTaskUpgrader {

    private static final String TASK_ID = "domain_password_settings_migration";
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "Domain Password Policy can't be upgraded, other instance may process them or an upgrader has failed previously";
    public static final String DEFAULT_POLICY_NAME = "default";

    private final DomainService domainService;

    private final PasswordPolicyService passwordPolicyService;

    public DomainPasswordPoliciesUpgrader(@Lazy SystemTaskRepository systemTaskRepository, DomainService domainService, PasswordPolicyService passwordPolicyService) {
        super(systemTaskRepository);
        this.domainService = domainService;
        this.passwordPolicyService = passwordPolicyService;
    }

    @Override
    protected Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String previousOperationId) {
        return updateSystemTask(task, (SystemTaskStatus.ONGOING), previousOperationId)
                .flatMap(updatedTask -> {
                    if (updatedTask.getOperationId().equals(instanceOperationId)) {
                        return migrateDomainPasswordSettings(updatedTask)
                                .andThen(Single.just(true))
                                .onErrorResumeNext((err) -> {
                                    log.error("Unable to migrate password settings (task: {}): {}", TASK_ID, err.getMessage());
                                    return Single.just(false);
                                });
                    } else {
                        return Single.error(new IllegalStateException("Task " + getTaskId() + " already processed by another instance : trigger a retry"));
                    }
                });
    }

    private Completable migrateDomainPasswordSettings(SystemTask task) {
        return domainService.listAll()
                .filter(domain -> domain.getPasswordSettings() != null)
                .flatMapSingle(domain -> {
                        final var policy = domain.getPasswordSettings().toPasswordPolicy();
                        policy.setName(DEFAULT_POLICY_NAME);
                        policy.setDefaultPolicy(true);
                        policy.setReferenceId(domain.getId());
                        policy.setReferenceType(ReferenceType.DOMAIN);
                        Date now = new Date();
                        policy.setCreatedAt(now);
                        policy.setUpdatedAt(now);
                        return passwordPolicyService.create(policy, null);
                })
                .ignoreElements()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId()).ignoreElement());
    }

    @Override
    protected IllegalStateException getIllegalStateException() {
        return new IllegalStateException(UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE);
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }

    @Override
    public int getOrder() {
        return DOMAIN_PASSWORD_POLICIES_UPGRADER;
    }
}
