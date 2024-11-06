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
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.PasswordPolicyNotFoundException;
import io.gravitee.am.service.impl.PasswordPolicyServiceImpl;
import io.gravitee.am.service.model.UpdatePasswordPolicy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rafal PODLES (rafal.podles at graviteesource.com)
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("unchecked")
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
    private IdentityProviderService identityProviderService;

    @Mock
    private User principal;

    @Test
    public void shouldCreate_noExistingDefaultPolicy() {
        PasswordPolicy newPasswordPolicy = createPolicy();

        when(passwordPolicyRepository.create(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.empty());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<PasswordPolicy> observer = cut.create(newPasswordPolicy, principal).test();

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
                        passwordPolicy.getPasswordHistoryEnabled().equals(newPasswordPolicy.getPasswordHistoryEnabled()) &&
                        passwordPolicy.getDefaultPolicy().equals(Boolean.TRUE));

        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.CREATE)));
    }

    @Test
    public void shouldCreate_withExistingDefaultPolicy() {
        var newPasswordPolicy = createPolicy();

        when(passwordPolicyRepository.create(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.just(new PasswordPolicy()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<PasswordPolicy> observer = cut.create(newPasswordPolicy, principal).test();

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
                        passwordPolicy.getPasswordHistoryEnabled().equals(newPasswordPolicy.getPasswordHistoryEnabled()) &&
                        passwordPolicy.getDefaultPolicy().equals(Boolean.FALSE));

        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.CREATE)));
    }

    @Test
    public void shouldCreate_withDefault_withExistingDefaultPolicy() {
        var newPasswordPolicy = createPolicy();
        newPasswordPolicy.setDefaultPolicy(Boolean.TRUE);

        when(passwordPolicyRepository.create(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.just(new PasswordPolicy()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<PasswordPolicy> observer = cut.create(newPasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertValue(passwordPolicy ->
                passwordPolicy.getCreatedAt() != null &&
                        passwordPolicy.getUpdatedAt() != null &&
                        passwordPolicy.getReferenceId().equals(DOMAIN_ID) &&
                        passwordPolicy.getReferenceType().equals(ReferenceType.DOMAIN) &&
                        passwordPolicy.getName().equals(newPasswordPolicy.getName()) &&
                        passwordPolicy.getDefaultPolicy().equals(Boolean.FALSE));

        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.CREATE)));
    }

    @Test
    public void shouldRejectCreate_MissingReference() {
        var newPasswordPolicy = new PasswordPolicy();
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

        TestObserver<PasswordPolicy> observer = cut.create(newPasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);
        verify(passwordPolicyRepository, never()).create(any());
        verify(auditService, never()).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService, never()).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.CREATE)));
    }

    @Test
    public void shouldLogFailure_Audit_on_CreationError() {
        var newPasswordPolicy = new PasswordPolicy();
        newPasswordPolicy.setName(UUID.randomUUID().toString());
        newPasswordPolicy.setReferenceType(ReferenceType.DOMAIN);
        newPasswordPolicy.setReferenceId(DOMAIN_ID);

        when(passwordPolicyRepository.create(any())).thenReturn(Single.error(new TechnicalException()));
        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.just(new PasswordPolicy()));

        TestObserver<PasswordPolicy> observer = cut.create(newPasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalException.class);

        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.FAILURE)));
        verify(eventService, never()).create(any());
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
        var existingPolicy = createPasswordPolicy();
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

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(existingPolicy));
        when(passwordPolicyRepository.update(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

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

        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    @Test
    public void shouldUpdateDefaultPolicy() {
        var existingPolicy = createPasswordPolicy();
        existingPolicy.setDefaultPolicy(Boolean.TRUE);
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName(UUID.randomUUID().toString());
        updatePasswordPolicy.setDefaultPolicy(Boolean.FALSE);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(existingPolicy));
        when(passwordPolicyRepository.update(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

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
                        passwordPolicy.getDefaultPolicy().equals(existingPolicy.getDefaultPolicy()));

        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    @Test
    public void shouldUpdateNotDefaultPolicy() {
        var existingPolicy = createPasswordPolicy();
        existingPolicy.setDefaultPolicy(Boolean.FALSE);
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName(UUID.randomUUID().toString());
        updatePasswordPolicy.setDefaultPolicy(Boolean.TRUE);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(existingPolicy));
        when(passwordPolicyRepository.update(any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

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
                        passwordPolicy.getDefaultPolicy().equals(existingPolicy.getDefaultPolicy()));

        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    @Test
    public void shouldNotUpdate_UnknownPolicy() {
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName(UUID.randomUUID().toString());
        updatePasswordPolicy.setMaxLength(17);
        updatePasswordPolicy.setLettersInMixedCase(Boolean.FALSE);
        updatePasswordPolicy.setMaxConsecutiveLetters(4);
        updatePasswordPolicy.setPasswordHistoryEnabled(Boolean.FALSE);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.empty());
        TestObserver<PasswordPolicy> observer = cut.update(ReferenceType.DOMAIN, DOMAIN_ID, UUID.randomUUID().toString(), updatePasswordPolicy, principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(PasswordPolicyNotFoundException.class);
        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.FAILURE)));
        verify(eventService, never()).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    @Test
    public void shouldSetDefaultPasswordPolicy_lackOfDefaultPolicy() {
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.empty());

        PasswordPolicy existingPolicy = createPasswordPolicy();
        existingPolicy.setDefaultPolicy(Boolean.FALSE);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(existingPolicy));

        existingPolicy.setDefaultPolicy(Boolean.TRUE);

        when(passwordPolicyRepository.update(any())).thenReturn(Single.just(existingPolicy));

        TestObserver<PasswordPolicy> observer = cut.setDefaultPasswordPolicy(ReferenceType.DOMAIN, DOMAIN_ID, UUID.randomUUID().toString(), principal).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        verify(passwordPolicyRepository).update(ArgumentMatchers.argThat(pp -> pp.getDefaultPolicy().equals(Boolean.TRUE)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    @Test
    public void shouldSetDefaultPasswordPolicy_changeDefaultPolicy() {
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        PasswordPolicy existingPolicy = createPasswordPolicy();
        existingPolicy.setName("existingDefault");
        existingPolicy.setDefaultPolicy(Boolean.FALSE);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(existingPolicy));

        PasswordPolicy defaultPolicy = createPasswordPolicy();
        defaultPolicy.setName("newDefault");
        defaultPolicy.setDefaultPolicy(Boolean.TRUE);

        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.just(defaultPolicy));

        when(
                passwordPolicyRepository.update(any())).thenReturn(Single.just(defaultPolicy), Single.just(existingPolicy));

        TestObserver<PasswordPolicy> observer = cut.setDefaultPasswordPolicy(ReferenceType.DOMAIN, DOMAIN_ID, UUID.randomUUID().toString(), principal).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        ArgumentCaptor<PasswordPolicy> argument = ArgumentCaptor.forClass(PasswordPolicy.class);
        verify(passwordPolicyRepository, times(2)).update(argument.capture());
        argument.getAllValues();
        Assertions.assertFalse(argument.getAllValues().get(0).getDefaultPolicy());
        Assertions.assertTrue(argument.getAllValues().get(1).getDefaultPolicy());
        verify(eventService, times(2)).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.UPDATE)));
    }

    private PasswordPolicy createPolicy() {
        var passwordPolicy = new PasswordPolicy();
        passwordPolicy.setReferenceType(ReferenceType.DOMAIN);
        passwordPolicy.setReferenceId(DOMAIN_ID);
        passwordPolicy.setName(UUID.randomUUID().toString());
        passwordPolicy.setMaxLength(18);
        passwordPolicy.setMinLength(8);
        passwordPolicy.setOldPasswords((short) 5);
        passwordPolicy.setExcludePasswordsInDictionary(Boolean.FALSE);
        passwordPolicy.setExcludeUserProfileInfoInPassword(Boolean.TRUE);
        passwordPolicy.setExpiryDuration(456);
        passwordPolicy.setIncludeNumbers(Boolean.TRUE);
        passwordPolicy.setIncludeSpecialCharacters(Boolean.FALSE);
        passwordPolicy.setLettersInMixedCase(Boolean.TRUE);
        passwordPolicy.setMaxConsecutiveLetters(3);
        passwordPolicy.setPasswordHistoryEnabled(Boolean.FALSE);
        return passwordPolicy;
    }

    private PasswordPolicy createPasswordPolicy() {
        var passwordPolicy = new PasswordPolicy();
        passwordPolicy.setId(UUID.randomUUID().toString());
        passwordPolicy.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        passwordPolicy.setUpdatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        passwordPolicy.setReferenceId(DOMAIN_ID);
        passwordPolicy.setReferenceType(ReferenceType.DOMAIN);
        passwordPolicy.setName(UUID.randomUUID().toString());
        passwordPolicy.setMaxLength(18);
        passwordPolicy.setMinLength(8);
        passwordPolicy.setOldPasswords((short) 5);
        passwordPolicy.setExcludePasswordsInDictionary(Boolean.FALSE);
        passwordPolicy.setExcludeUserProfileInfoInPassword(Boolean.TRUE);
        passwordPolicy.setExpiryDuration(456);
        passwordPolicy.setIncludeNumbers(Boolean.TRUE);
        passwordPolicy.setIncludeSpecialCharacters(Boolean.FALSE);
        passwordPolicy.setLettersInMixedCase(Boolean.TRUE);
        passwordPolicy.setMaxConsecutiveLetters(3);
        passwordPolicy.setPasswordHistoryEnabled(Boolean.FALSE);
        return passwordPolicy;
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
        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.empty());

        var policyObserver = cut.retrievePasswordPolicy(user, passwordSettingsAware, provider).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertNoValues();
    }

    @Test
    public void shouldNotRetrievePolicy_noPolicyDefined() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setSource("idp-id");

        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.empty());

        var policyObserver = cut.retrievePasswordPolicy(user, null, null).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());
        verify(passwordPolicyRepository, times(1)).findByDefaultPolicy(any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertNoValues();
    }

    @Test
    public void shouldRetrieve_DefaultPasswordPolicy_noPolicyDefined_atApplication_or_IdpLevel() {
        var user = new io.gravitee.am.model.User();
        user.setSource("idp-id");
        var app = new Application();
        var idp = new IdentityProvider();

        var policy = new PasswordPolicy();
        policy.setId(UUID.randomUUID().toString());

        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.just(policy));

        var policyObserver = cut.retrievePasswordPolicy(user, app, idp).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());
        verify(passwordPolicyRepository, times(1)).findByDefaultPolicy(any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertValue(result -> result.getId().equals(policy.getId()));
    }

    @Test
    public void shouldRetrieve_DefaultPasswordPolicy_appSettings_inherited() {
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

        when(passwordPolicyRepository.findByDefaultPolicy(any(), any())).thenReturn(Maybe.just(policy));

        var policyObserver = cut.retrievePasswordPolicy(user, app, idp).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());
        verify(passwordPolicyRepository, times(1)).findByDefaultPolicy(any(), any());

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

        var policyObserver = cut.retrievePasswordPolicy(user, app, idp).test();

        verify(passwordPolicyRepository, never()).findByReferenceAndId(any(), any(), any());
        verify(passwordPolicyRepository, never()).findByDefaultPolicy(any(), any());

        policyObserver.awaitDone(5, TimeUnit.SECONDS);
        policyObserver.assertValue(result -> Objects.equals(result.getMaxLength(), passwordSettings.getMaxLength()));
    }

    @Test
    public void shouldNotRetrieve_idpPolicy_But_AppPolicy() {
        // app policy set inherited to false, so the app policy is expected
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
        policyObserver.assertValue(result -> Objects.equals(result.getMaxLength(), passwordSettings.getMaxLength()));
    }

    @Test
    public void shouldRetrieve_idpPolicy_as_AppPolicy_is_inherited() {
        // app policy set inherited to false, so the app policy is expected
        var user = new io.gravitee.am.model.User();
        user.setSource("idp-id");

        var app = new Application();
        var settings = new ApplicationSettings();
        var passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(true);
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
        policyObserver.assertValue(result -> Objects.equals(result.getMaxLength(), policy.getMaxLength()));
    }

    @Test
    public void shouldDeleteAndUpdateIdpPoliciesByReference() {
        when(passwordPolicyRepository.deleteByReference(any(), any())).thenReturn(Completable.complete());

        cut.deleteByReference(ReferenceType.DOMAIN, DOMAIN_ID)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        verify(passwordPolicyRepository).deleteByReference(any(), any());
    }

    @Test
    public void shouldDeleteAndUpdateIdpPolicyAndUpdateIdp() {
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        passwordPolicy.setId(UUID.randomUUID().toString());
        passwordPolicy.setReferenceType(ReferenceType.DOMAIN);
        passwordPolicy.setReferenceId(DOMAIN_ID);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(passwordPolicy));
        when(identityProviderService.findWithPasswordPolicy(any(), any(), any())).thenReturn(Flowable.just(new IdentityProvider(), new IdentityProvider()));
        when(identityProviderService.updatePasswordPolicy(any(), any(), any())).thenReturn(Single.just(new IdentityProvider()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(passwordPolicyRepository.delete(any())).thenReturn(Completable.complete());

        final var observer = cut.deleteAndUpdateIdp(ReferenceType.DOMAIN, DOMAIN_ID, passwordPolicy.getId(), principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        verify(passwordPolicyRepository).delete(any());
        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.DELETE)));
    }

    @Test
    public void shouldDeleteAndUpdateIdpPolicy_NoIdp_ToUpdate() {
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        passwordPolicy.setId(UUID.randomUUID().toString());
        passwordPolicy.setReferenceId(DOMAIN_ID);
        passwordPolicy.setReferenceType(ReferenceType.DOMAIN);
        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(passwordPolicy));
        when(identityProviderService.findWithPasswordPolicy(any(), any(), any())).thenReturn(Flowable.empty());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(passwordPolicyRepository.delete(any())).thenReturn(Completable.complete());

        final var observer = cut.deleteAndUpdateIdp(ReferenceType.DOMAIN, DOMAIN_ID, passwordPolicy.getId(), principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        verify(passwordPolicyRepository).delete(any());
        verify(passwordPolicyRepository, never()).findByOldest(any(), any());
        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.DELETE)));
        verify(identityProviderService, never()).updatePasswordPolicy(any(), any(), any());

    }

    @Test
    public void shouldIgnoreDeleteAndUpdateIdpPolicy_unknownPolicy() {
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        passwordPolicy.setId(UUID.randomUUID().toString());

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.empty());

        final var observer = cut.deleteAndUpdateIdp(ReferenceType.DOMAIN, DOMAIN_ID, passwordPolicy.getId(), principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        verify(passwordPolicyRepository, never()).delete(any());
        verify(passwordPolicyRepository, never()).findByOldest(any(), any());
        verify(auditService, never()).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(eventService, never()).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.DELETE)));
        verify(identityProviderService, never()).updatePasswordPolicy(any(), any(), any());
    }

    @Test
    public void shouldNotDeleteAndUpdateIdpPolicyIfIdpNotUpdated() {
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        passwordPolicy.setId(UUID.randomUUID().toString());
        passwordPolicy.setReferenceType(ReferenceType.DOMAIN);
        passwordPolicy.setReferenceId(DOMAIN_ID);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(passwordPolicy));
        when(identityProviderService.findWithPasswordPolicy(any(), any(), any())).thenReturn(Flowable.just(new IdentityProvider()));
        when(identityProviderService.updatePasswordPolicy(any(), any(), any())).thenReturn(Single.error(new TechnicalException()));

        Completable deleteResponse = Completable.create(co -> co.setDisposable(Disposable.empty()));
        Single<Event> eventResponse = Single.unsafeCreate(co -> co.onSubscribe(Disposable.empty()));

        when(passwordPolicyRepository.delete(any())).thenReturn(deleteResponse);
        when(eventService.create(any())).thenReturn(eventResponse);

        final var observer = cut.deleteAndUpdateIdp(ReferenceType.DOMAIN, DOMAIN_ID, passwordPolicy.getId(), principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalException.class);
        observer.assertNotComplete();

        deleteResponse.test().assertNotComplete();
        eventResponse.test().assertNotComplete();

        verify(passwordPolicyRepository).delete(any());
        verify(passwordPolicyRepository, never()).findByOldest(any(), any());
        verify(auditService).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.FAILURE)));
        verify(eventService).create(ArgumentMatchers.argThat(evt -> evt.getType().equals(Type.PASSWORD_POLICY) && evt.getPayload().getAction().equals(Action.DELETE)));
    }

    @Test
    public void shouldDelete_AndUpdateIdps_AndChangeDefaultPasswordPolicy() {
        PasswordPolicy somePasswordPolicy = new PasswordPolicy();
        somePasswordPolicy.setId(UUID.randomUUID().toString());
        somePasswordPolicy.setDefaultPolicy(Boolean.FALSE);
        somePasswordPolicy.setReferenceId(DOMAIN_ID);
        somePasswordPolicy.setReferenceType(ReferenceType.DOMAIN);

        PasswordPolicy passwordPolicy = new PasswordPolicy();
        passwordPolicy.setId(UUID.randomUUID().toString());
        passwordPolicy.setDefaultPolicy(Boolean.TRUE);
        passwordPolicy.setReferenceId(DOMAIN_ID);
        passwordPolicy.setReferenceType(ReferenceType.DOMAIN);

        when(passwordPolicyRepository.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(passwordPolicy));
        when(passwordPolicyRepository.findByOldest(any(), any())).thenReturn(Maybe.just(somePasswordPolicy));
        when(passwordPolicyRepository.update(any())).thenReturn(Single.just(somePasswordPolicy));

        when(identityProviderService.findWithPasswordPolicy(any(), any(), any())).thenReturn(Flowable.just(new IdentityProvider(), new IdentityProvider()));
        when(identityProviderService.updatePasswordPolicy(any(), any(), any())).thenReturn(Single.just(new IdentityProvider()));

        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventService.create(eventArgumentCaptor.capture())).thenReturn(Single.just(new Event()));

        when(passwordPolicyRepository.delete(any())).thenReturn(Completable.complete());

        final var observer = cut.deleteAndUpdateIdp(ReferenceType.DOMAIN, DOMAIN_ID, passwordPolicy.getId(), principal).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        verify(passwordPolicyRepository).delete(any());
        verify(passwordPolicyRepository, times(1)).findByOldest(any(), any());
        verify(passwordPolicyRepository, times(1)).update(any());
        verify(auditService, times(2)).report(ArgumentMatchers.argThat(builder -> builder.build(MAPPER).getOutcome().getStatus().equals(Status.SUCCESS)));
        List<Event> allValues = eventArgumentCaptor.getAllValues();
        Assertions.assertEquals(allValues.size(), 2);
        Assertions.assertEquals(Action.DELETE, allValues.get(0).getPayload().getAction());
        Assertions.assertEquals(Type.PASSWORD_POLICY, allValues.get(0).getType());
        Assertions.assertEquals(Action.UPDATE, allValues.get(1).getPayload().getAction());
        Assertions.assertEquals(Type.PASSWORD_POLICY, allValues.get(1).getType());
    }

}
