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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.PasswordSettingsAware;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.PasswordPolicyRepository;
import io.gravitee.am.service.exception.PasswordPolicyNotFoundException;
import io.gravitee.am.service.impl.PasswordPolicyServiceImpl;
import io.gravitee.am.service.model.NewPasswordPolicy;
import io.gravitee.am.service.model.UpdatePasswordPolicy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rafal PODLES (rafal.podles at graviteesource.com)
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
    private EventService eventService;

    @Mock
    private User principal;

    @Test
    public void shouldCreate() {
        var newPasswordPolicy = new NewPasswordPolicy();
        newPasswordPolicy.setName(UUID.randomUUID().toString());
        newPasswordPolicy.setMaxLength(18);
        newPasswordPolicy.setMinLength(8);
        newPasswordPolicy.setOldPasswords((short) 5);
        newPasswordPolicy.setExcludePasswordsInDictionary(Boolean.FALSE);
        newPasswordPolicy.setExcludeUserProfileInfoInPassword(Boolean.TRUE);
        newPasswordPolicy.setExpiryDuration(456);
        newPasswordPolicy.setIncludeNumbers(Boolean.TRUE);
        newPasswordPolicy.setIncludeSpecialCharacters(Boolean.FALSE);
        newPasswordPolicy.setLettersInMixedCase(Boolean.TRUE);
        newPasswordPolicy.setMaxConsecutiveLetters(3);
        newPasswordPolicy.setPasswordHistoryEnabled(Boolean.FALSE);

        Mockito.when(passwordPolicyRepository.create(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        Mockito.when(eventService.create(any())).thenReturn(Single.just(new Event()));

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
        Mockito.verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.CREATE)));
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
        Mockito.verify(eventService, never()).create(any());
    }

    @Test
    public void shouldFindByDomain() {
        when(passwordPolicyRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_ID))
                .thenReturn(Flowable.just(new PasswordPolicy()));
        TestSubscriber<PasswordPolicy> testObserver = cut.findByDomain(DOMAIN_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldUpdate() {
        var existingPolicy = new PasswordPolicy();
        existingPolicy.setId(UUID.randomUUID().toString());
        existingPolicy.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        existingPolicy.setUpdatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        existingPolicy.setReferenceId(DOMAIN_ID);
        existingPolicy.setReferenceType(ReferenceType.DOMAIN);
        existingPolicy.setName(UUID.randomUUID().toString());
        existingPolicy.setMaxLength(18);
        existingPolicy.setMinLength(8);
        existingPolicy.setOldPasswords((short) 5);
        existingPolicy.setExcludePasswordsInDictionary(Boolean.FALSE);
        existingPolicy.setExcludeUserProfileInfoInPassword(Boolean.TRUE);
        existingPolicy.setExpiryDuration(456);
        existingPolicy.setIncludeNumbers(Boolean.TRUE);
        existingPolicy.setIncludeSpecialCharacters(Boolean.FALSE);
        existingPolicy.setLettersInMixedCase(Boolean.TRUE);
        existingPolicy.setMaxConsecutiveLetters(3);
        existingPolicy.setPasswordHistoryEnabled(Boolean.FALSE);

        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName(UUID.randomUUID().toString());
        updatePasswordPolicy.setMaxLength(17);
        updatePasswordPolicy.setMinLength(9);
        updatePasswordPolicy.setOldPasswords((short) 4);
        updatePasswordPolicy.setExcludePasswordsInDictionary(Boolean.TRUE);
        updatePasswordPolicy.setExcludeUserProfileInfoInPassword(Boolean.FALSE);
        updatePasswordPolicy.setExpiryDuration(654);
        updatePasswordPolicy.setIncludeNumbers(Boolean.FALSE);
        updatePasswordPolicy.setIncludeSpecialCharacters(Boolean.TRUE);
        updatePasswordPolicy.setLettersInMixedCase(Boolean.FALSE);
        updatePasswordPolicy.setMaxConsecutiveLetters(4);
        updatePasswordPolicy.setPasswordHistoryEnabled(Boolean.FALSE);

        Mockito.when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(existingPolicy));
        Mockito.when(passwordPolicyRepository.update(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        Mockito.when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<PasswordPolicy> observer = cut.update(ReferenceType.DOMAIN, DOMAIN_ID, existingPolicy.getId(), updatePasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertValue(passwordPolicy ->
                        passwordPolicy.getId().equals(existingPolicy.getId()) &&
                        passwordPolicy.getCreatedAt().equals(existingPolicy.getCreatedAt()) &&
                        !passwordPolicy.getUpdatedAt().equals(existingPolicy.getUpdatedAt()) &&
                        passwordPolicy.getReferenceId().equals(DOMAIN_ID) &&
                        passwordPolicy.getReferenceType().equals(ReferenceType.DOMAIN) &&
                        passwordPolicy.getName().equals(updatePasswordPolicy.getName()) &&
                        passwordPolicy.getMaxLength().equals(updatePasswordPolicy.getMaxLength()) &&
                        passwordPolicy.getMinLength().equals(updatePasswordPolicy.getMinLength()) &&
                        passwordPolicy.getOldPasswords().equals(updatePasswordPolicy.getOldPasswords()) &&
                        passwordPolicy.getExcludePasswordsInDictionary().equals(updatePasswordPolicy.getExcludePasswordsInDictionary()) &&
                        passwordPolicy.getExcludeUserProfileInfoInPassword().equals(updatePasswordPolicy.getExcludeUserProfileInfoInPassword()) &&
                        passwordPolicy.getExpiryDuration().equals(updatePasswordPolicy.getExpiryDuration()) &&
                        passwordPolicy.getIncludeNumbers().equals(updatePasswordPolicy.getIncludeNumbers()) &&
                        passwordPolicy.getIncludeSpecialCharacters().equals(updatePasswordPolicy.getIncludeSpecialCharacters()) &&
                        passwordPolicy.getLettersInMixedCase().equals(updatePasswordPolicy.getLettersInMixedCase()) &&
                        passwordPolicy.getMaxConsecutiveLetters().equals(updatePasswordPolicy.getMaxConsecutiveLetters()) &&
                        passwordPolicy.getPasswordHistoryEnabled().equals(updatePasswordPolicy.getPasswordHistoryEnabled()));

        Mockito.verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        Mockito.verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    @Test
    public void shouldNotUpdate_UnknownPolicy() {
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName(UUID.randomUUID().toString());
        updatePasswordPolicy.setMaxLength(17);
        updatePasswordPolicy.setLettersInMixedCase(Boolean.FALSE);
        updatePasswordPolicy.setMaxConsecutiveLetters(4);
        updatePasswordPolicy.setPasswordHistoryEnabled(Boolean.FALSE);

        Mockito.when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.empty());
        TestObserver<PasswordPolicy> observer = cut.update(ReferenceType.DOMAIN, DOMAIN_ID, UUID.randomUUID().toString(), updatePasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(PasswordPolicyNotFoundException.class);
        Mockito.verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.FAILURE)));
        Mockito.verify(eventService, never()).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    @Test
    public void shouldNotRetrieve_PasswordPolicy_noPolicyDefined() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setSource("idp-id");

        innerShouldNotRetrieve_PasswordPolicy_noPolicyDefined(user, null, null); // no app, no provider
        innerShouldNotRetrieve_PasswordPolicy_noPolicyDefined(user, null, new IdentityProvider()); // empty policy id
        innerShouldNotRetrieve_PasswordPolicy_noPolicyDefined(user, new Application(), new IdentityProvider()); // empty policy id & null app settings
    }

    private void innerShouldNotRetrieve_PasswordPolicy_noPolicyDefined(io.gravitee.am.model.User user, PasswordSettingsAware passwordSettingsAware, IdentityProvider provider) {
        when(passwordPolicyRepository.findByReference(any(), any())).thenReturn(Flowable.empty());

        var policyObserver = cut.retrievePasswordPolicy(user, passwordSettingsAware, provider).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertNoValues();
    }

    @Test
    public void shouldRetrieve_DefaultPasswordPolicy_noPolicyDefined_atApp() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setSource("idp-id");

        when(passwordPolicyRepository.findByReference(any(), any())).thenReturn(Flowable.empty());

        var policyObserver = cut.retrievePasswordPolicy(user, null, null).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertNoValues();
    }

    @Test
    public void shouldRetrieve_DefaultPasswordPolicy_noPolicyDefined_atApplication() {
        var user = new io.gravitee.am.model.User();
        user.setSource("idp-id");
        var app = new Application();
        var idp = new IdentityProvider();

        var policy = new PasswordPolicy();
        policy.setId(UUID.randomUUID().toString());

        when(passwordPolicyRepository.findByReference(any(), any())).thenReturn(Flowable.just(policy));

        var policyObserver = cut.retrievePasswordPolicy(user, app, idp).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertValue(result -> result.getId().equals(policy.getId()));
    }

    @Test
    public void shouldRetrieve_DefaultPasswordPolicy_appSettings_inherite() {
        var user = new io.gravitee.am.model.User();
        user.setSource("idp-id");

        var app = new Application();
        var settings = new ApplicationSettings();
        var passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(true);
        settings.setPasswordSettings(passwordSettings);
        app.setSettings(settings);

        var idp = new IdentityProvider();

        var policy = new PasswordPolicy();
        policy.setId(UUID.randomUUID().toString());

        when(passwordPolicyRepository.findByReference(any(), any())).thenReturn(Flowable.just(policy));

        var policyObserver = cut.retrievePasswordPolicy(user, app, idp).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertValue(result -> result.getId().equals(policy.getId()));
    }

    @Test
    public void shouldRetrieve_appSettings() {
        var user = new io.gravitee.am.model.User();
        user.setSource("idp-id");

        var app = new Application();
        var settings = new ApplicationSettings();
        var passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(false);
        passwordSettings.setMaxLength(64);
        settings.setPasswordSettings(passwordSettings);
        app.setSettings(settings);

        var idp = new IdentityProvider();

        var policy = new PasswordPolicy();
        policy.setMaxLength(128);

        when(passwordPolicyRepository.findByReference(any(), any())).thenReturn(Flowable.just(policy));

        var policyObserver = cut.retrievePasswordPolicy(user, app, idp).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertValue(result -> result.getMaxLength() == passwordSettings.getMaxLength());
    }

    @Test
    public void shouldRetrieve_idpPolicy() {
        var user = new io.gravitee.am.model.User();
        user.setSource("idp-id");

        var app = new Application();
        var settings = new ApplicationSettings();
        var passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(false);
        passwordSettings.setMaxLength(64);
        settings.setPasswordSettings(passwordSettings);
        app.setSettings(settings);

        var policy = new PasswordPolicy();
        policy.setMaxLength(128);
        policy.setId(UUID.randomUUID().toString());

        var idp = new IdentityProvider();
        idp.setPasswordPolicy(policy.getId());

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), eq(policy.getId()))).thenReturn(Maybe.just(policy));

        var policyObserver = cut.retrievePasswordPolicy(user, app, idp).test();

        verify(passwordPolicyRepository, never()).findByReference(any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertValue(result -> result.getMaxLength() == policy.getMaxLength());
    }
}
