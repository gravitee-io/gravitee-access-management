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
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.PasswordPolicyRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.model.NewPasswordPolicy;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.PasswordPolicyAuditBuilder;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    @Autowired
    @Lazy
    private PasswordPolicyRepository passwordPolicyRepository;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<PasswordPolicy> create(ReferenceType referenceType, String referenceId, NewPasswordPolicy policy, User principal) {
        log.debug("Create a new password policy named '{}' for {} {}", policy.getName(), referenceType, referenceId);

        final var entity = policy.toPasswordPolicy(referenceType, referenceId);
        // TODO during AM-2893, check if there is existing policies, if not set this one as default

        return passwordPolicyRepository.create(entity)
                .doOnSuccess(createdPolicy -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.PASSWORD_POLICY_CREATED)
                        .policy(createdPolicy)))
                .doOnError(error -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.PASSWORD_POLICY_CREATED).throwable(error)));
    }
}
