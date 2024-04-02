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

package io.gravitee.am.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.PasswordPolicyRepository;
import io.gravitee.am.service.impl.PasswordPolicyServiceImpl;
import io.gravitee.am.service.model.NewPasswordPolicy;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PasswordPolicyServiceTest {
    private static final String DOMAIN_ID = UUID.randomUUID().toString();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @InjectMocks
    private final PasswordPolicyService cut = new PasswordPolicyServiceImpl();

    @Mock
    private PasswordPolicyRepository passwordPolicyRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private User principal;

    @Test
    public void shouldCreate() {
        var newPasswordPolicy = new NewPasswordPolicy();
        newPasswordPolicy.setName(UUID.randomUUID().toString());
        newPasswordPolicy.setMaxLength(18);
        newPasswordPolicy.setMinLength(8);
        newPasswordPolicy.setOldPasswords((short)5);
        newPasswordPolicy.setExcludePasswordsInDictionary(Boolean.FALSE);
        newPasswordPolicy.setExcludeUserProfileInfoInPassword(Boolean.TRUE);
        newPasswordPolicy.setExpiryDuration(456);
        newPasswordPolicy.setIncludeNumbers(Boolean.TRUE);
        newPasswordPolicy.setIncludeSpecialCharacters(Boolean.FALSE);
        newPasswordPolicy.setLettersInMixedCase(Boolean.TRUE);
        newPasswordPolicy.setMaxConsecutiveLetters(3);
        newPasswordPolicy.setPasswordHistoryEnabled(Boolean.FALSE);

        Mockito.when(passwordPolicyRepository.create(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));

        TestObserver<PasswordPolicy> observer = cut.create(ReferenceType.DOMAIN, DOMAIN_ID, newPasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertValue(passwordPolicy ->
                passwordPolicy.getCreatedAt() != null &&
                passwordPolicy.getUpdatedAt() != null &&
                passwordPolicy.getReferenceId().equals(DOMAIN_ID) &&
                passwordPolicy.getReferenceType().equals(ReferenceType.DOMAIN) &&
                passwordPolicy.getName().equals(newPasswordPolicy.getName()) &&
                passwordPolicy.getMaxLength().equals(newPasswordPolicy.getMaxLength()) &&
                passwordPolicy.getMinLength().equals(newPasswordPolicy.getMinLength()) &&
                passwordPolicy.getOldPasswords().equals(newPasswordPolicy.getOldPasswords()) &&
                passwordPolicy.getExcludePasswordsInDictionary().equals(newPasswordPolicy.getExcludePasswordsInDictionary()) &&
                passwordPolicy.getExcludeUserProfileInfoInPassword().equals(newPasswordPolicy.getExcludeUserProfileInfoInPassword()) &&
                passwordPolicy.getExpiryDuration().equals(newPasswordPolicy.getExpiryDuration()) &&
                passwordPolicy.getIncludeNumbers().equals(newPasswordPolicy.getIncludeNumbers()) &&
                passwordPolicy.getIncludeSpecialCharacters().equals(newPasswordPolicy.getIncludeSpecialCharacters()) &&
                passwordPolicy.getLettersInMixedCase().equals(newPasswordPolicy.getLettersInMixedCase()) &&
                passwordPolicy.getMaxConsecutiveLetters().equals(newPasswordPolicy.getMaxConsecutiveLetters()) &&
                passwordPolicy.getPasswordHistoryEnabled().equals(newPasswordPolicy.getPasswordHistoryEnabled()));

        Mockito.verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
    }

    @Test
    public void shouldLogFailure_Audit_on_CreationError() {
        var newPasswordPolicy = new NewPasswordPolicy();
        newPasswordPolicy.setName(UUID.randomUUID().toString());

        Mockito.when(passwordPolicyRepository.create(any())).thenReturn(Single.error(new TechnicalException()));

        TestObserver<PasswordPolicy> observer = cut.create(ReferenceType.DOMAIN, DOMAIN_ID, newPasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalException.class);

        Mockito.verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.FAILURE)));
    }

}
