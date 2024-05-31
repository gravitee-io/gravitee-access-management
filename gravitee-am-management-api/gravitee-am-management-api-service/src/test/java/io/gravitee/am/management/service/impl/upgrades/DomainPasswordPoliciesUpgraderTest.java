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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.PasswordPolicyService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DomainPasswordPoliciesUpgraderTest {



    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private DomainService domainService;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @InjectMocks
    private DomainPasswordPoliciesUpgrader upgrader;


    @Test
    void should_ignore_if_task_completed() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.just(task));

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(any());
        verify(domainService, never()).listAll();
    }

    @Test
    void should_ignore_domain_without_passwordSettings() {
        initializeSystemTask();
        var domainNoPasswordSettings = new Domain();

        when(domainService.listAll()).thenReturn(Flowable.just(domainNoPasswordSettings));

        upgrader.upgrade();

        verifySystemTask();
        verify(passwordPolicyService, never()).create(any(), any());
    }

    @Test
    void should_create_password_policy() {
        initializeSystemTask();

        final var domain = new Domain();
        domain.setId(UUID.randomUUID().toString());

        final var passwordSettings = new PasswordSettings();
        passwordSettings.setMaxLength(64);
        passwordSettings.setMinLength(8);
        passwordSettings.setExcludePasswordsInDictionary(true);
        domain.setPasswordSettings(passwordSettings);

        when(domainService.listAll()).thenReturn(Flowable.just(domain));
        when(passwordPolicyService.create(any(), any())).then(answerWith -> Single.just(answerWith.getArguments()[0]));

        upgrader.upgrade();

        verifySystemTask();
        verify(passwordPolicyService).create(argThat(checkPolicy(domain, passwordSettings)), eq(null));
    }

    @Test
    void should_create_password_policies() {
        initializeSystemTask();

        final var domainNoSettings = new Domain();
        domainNoSettings.setId(UUID.randomUUID().toString());

        final var domain = new Domain();
        domain.setId(UUID.randomUUID().toString());

        final var passwordSettings = new PasswordSettings();
        passwordSettings.setMaxLength(64);
        passwordSettings.setMinLength(8);
        passwordSettings.setExcludePasswordsInDictionary(true);
        domain.setPasswordSettings(passwordSettings);

        final var domain2 = new Domain();
        domain2.setId(UUID.randomUUID().toString());

        final var passwordSettings2 = new PasswordSettings();
        passwordSettings2.setMaxLength(128);
        passwordSettings2.setMinLength(8);
        passwordSettings2.setExcludePasswordsInDictionary(true);
        domain2.setPasswordSettings(passwordSettings2);

        when(domainService.listAll()).thenReturn(Flowable.just(domain, domainNoSettings, domain2));
        when(passwordPolicyService.create(any(), any())).then(answerWith -> Single.just(answerWith.getArguments()[0]));

        upgrader.upgrade();

        verifySystemTask();
        verify(passwordPolicyService, times(2)).create(any(), eq(null));
        verify(passwordPolicyService).create(argThat(checkPolicy(domain2, passwordSettings2)), eq(null));
        verify(passwordPolicyService).create(argThat(checkPolicy(domain, passwordSettings)), eq(null));
    }

    private static ArgumentMatcher<PasswordPolicy> checkPolicy(Domain domain2, PasswordSettings passwordSettings2) {
        return policy -> policy.getReferenceType().equals(ReferenceType.DOMAIN) &&
                policy.getReferenceId().equals(domain2.getId()) &&
                policy.getName().equals(DomainPasswordPoliciesUpgrader.PASSWORD_POLICY_NAME_DEFAULT) &&
                policy.getMaxLength().equals(passwordSettings2.getMaxLength()) &&
                policy.getMinLength().equals(passwordSettings2.getMinLength()) &&
                policy.getExcludePasswordsInDictionary() &&
                policy.getDefaultPolicy();
    }

    private void initializeSystemTask() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer((args) -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });
    }

    private void verifySystemTask() {
        verify(systemTaskRepository, times(1)).findById(anyString());
        verify(systemTaskRepository, times(2)).updateIf(any(), any());
    }
}
