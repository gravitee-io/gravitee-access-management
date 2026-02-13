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
package io.gravitee.am.gateway.handler.scim.service.impl;

import io.gravitee.am.gateway.handler.common.email.EmailContainer;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.gateway.api.EmailStagingRepository;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EmailStagingServiceImplTest {

    @InjectMocks
    private EmailStagingServiceImpl emailStagingService;

    @Mock
    private Domain domain;

    @Mock
    private EmailStagingRepository emailStagingRepository;

    @Mock
    private ActionLeaseRepository actionLeaseRepository;

    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    private static final String DOMAIN_ID = "domain-id-123";
    private static final String USER_ID = "user-id-456";
    private static final String USER_EMAIL = "user@example.com";
    private static final String CLIENT_ID = "client-id-789";
    private static final ReferenceType REFERENCE_TYPE = ReferenceType.DOMAIN;

    @BeforeEach
    void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(domain.getReferenceType()).thenReturn(REFERENCE_TYPE);
    }

    @Test
    void shouldPushEmailWithClient() {
        // Given
        User user = createUser();
        Client client = createClient();
        EmailContainer emailContainer = new EmailContainer(user, client);
        Template template = Template.REGISTRATION_CONFIRMATION;

        EmailStaging createdStaging = createEmailStaging();
        when(emailStagingRepository.create(any(EmailStaging.class)))
                .thenReturn(Single.just(createdStaging));

        // When
        TestObserver<Void> observer = emailStagingService.push(emailContainer, template).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();

        ArgumentCaptor<EmailStaging> captor = ArgumentCaptor.forClass(EmailStaging.class);
        verify(emailStagingRepository, times(1)).create(captor.capture());

        EmailStaging capturedStaging = captor.getValue();
        assertNotNull(capturedStaging);
        assertEquals(USER_ID, capturedStaging.getUserId());
        assertEquals(CLIENT_ID, capturedStaging.getApplicationId());
        assertEquals(DOMAIN_ID, capturedStaging.getReferenceId());
        assertEquals(REFERENCE_TYPE, capturedStaging.getReferenceType());
        assertEquals(template.name(), capturedStaging.getEmailTemplateName());

        verify(gatewayMetricProvider, times(1)).incrementStagingEmails();
    }

    @Test
    void shouldPushEmailWithoutClient() {
        // Given
        User user = createUser();
        EmailContainer emailContainer = new EmailContainer(user, null);
        Template template = Template.REGISTRATION_CONFIRMATION;

        EmailStaging createdStaging = createEmailStaging();
        when(emailStagingRepository.create(any(EmailStaging.class)))
                .thenReturn(Single.just(createdStaging));

        // When
        TestObserver<Void> observer = emailStagingService.push(emailContainer, template).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();

        ArgumentCaptor<EmailStaging> captor = ArgumentCaptor.forClass(EmailStaging.class);
        verify(emailStagingRepository, times(1)).create(captor.capture());

        EmailStaging capturedStaging = captor.getValue();
        assertNotNull(capturedStaging);
        assertEquals(USER_ID, capturedStaging.getUserId());
        assertNull(capturedStaging.getApplicationId());
        assertEquals(DOMAIN_ID, capturedStaging.getReferenceId());
        assertEquals(REFERENCE_TYPE, capturedStaging.getReferenceType());
        assertEquals(template.name(), capturedStaging.getEmailTemplateName());

        verify(gatewayMetricProvider, times(1)).incrementStagingEmails();
    }

    @Test
    void shouldHandleRepositoryError() {
        // Given
        User user = createUser();
        Client client = createClient();
        EmailContainer emailContainer = new EmailContainer(user, client);
        Template template = Template.REGISTRATION_CONFIRMATION;

        RuntimeException exception = new RuntimeException("Repository error");
        when(emailStagingRepository.create(any(EmailStaging.class)))
                .thenReturn(Single.error(exception));

        // When
        TestObserver<Void> observer = emailStagingService.push(emailContainer, template).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(RuntimeException.class);
        observer.assertError(throwable -> throwable.getMessage().equals("Repository error"));

        verify(emailStagingRepository, times(1)).create(any(EmailStaging.class));
        verify(gatewayMetricProvider, never()).incrementStagingEmails();
    }

    @Test
    void shouldSetAllRequiredFieldsFromDomainAndUser() {
        // Given
        User user = createUser();
        user.setEmail("test@example.com");
        Client client = createClient();
        EmailContainer emailContainer = new EmailContainer(user, client);
        Template template = Template.RESET_PASSWORD;

        EmailStaging createdStaging = createEmailStaging();
        when(emailStagingRepository.create(any(EmailStaging.class)))
                .thenReturn(Single.just(createdStaging));

        // When
        TestObserver<Void> observer = emailStagingService.push(emailContainer, template).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();

        ArgumentCaptor<EmailStaging> captor = ArgumentCaptor.forClass(EmailStaging.class);
        verify(emailStagingRepository, times(1)).create(captor.capture());

        EmailStaging capturedStaging = captor.getValue();
        assertNotNull(capturedStaging.getUserId());
        assertNotNull(capturedStaging.getReferenceId());
        assertNotNull(capturedStaging.getReferenceType());
        assertNotNull(capturedStaging.getEmailTemplateName());
        assertEquals(DOMAIN_ID, capturedStaging.getReferenceId());
        assertEquals(REFERENCE_TYPE, capturedStaging.getReferenceType());
    }

    @Test
    void shouldNotRequireActionLeaseForPush() {
        // Given
        User user = createUser();
        Client client = createClient();
        EmailContainer emailContainer = new EmailContainer(user, client);
        Template template = Template.REGISTRATION_CONFIRMATION;

        EmailStaging createdStaging = createEmailStaging();
        when(emailStagingRepository.create(any(EmailStaging.class)))
                .thenReturn(Single.just(createdStaging));

        // When
        TestObserver<Void> observer = emailStagingService.push(emailContainer, template).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();

        // Verify that ActionLeaseRepository is never called
        verify(actionLeaseRepository, never()).acquireLease(any(), any(), any());
        verify(actionLeaseRepository, never()).releaseLease(any(), any());
    }

    @Test
    void shouldIncrementMetricOnlyOnSuccess() {
        // Given
        User user = createUser();
        Client client = createClient();
        EmailContainer emailContainer = new EmailContainer(user, client);
        Template template = Template.REGISTRATION_CONFIRMATION;

        EmailStaging createdStaging = createEmailStaging();
        when(emailStagingRepository.create(any(EmailStaging.class)))
                .thenReturn(Single.just(createdStaging));

        // When
        emailStagingService.push(emailContainer, template).blockingAwait();

        // Then
        verify(gatewayMetricProvider, times(1)).incrementStagingEmails();
    }

    @Test
    void shouldPushWithVerifyAttemptTemplate() {
        // Given
        User user = createUser();
        Client client = createClient();
        EmailContainer emailContainer = new EmailContainer(user, client);
        Template template = Template.VERIFY_ATTEMPT;

        EmailStaging createdStaging = createEmailStaging();
        when(emailStagingRepository.create(any(EmailStaging.class)))
                .thenReturn(Single.just(createdStaging));

        // When
        TestObserver<Void> observer = emailStagingService.push(emailContainer, template).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();

        ArgumentCaptor<EmailStaging> captor = ArgumentCaptor.forClass(EmailStaging.class);
        verify(emailStagingRepository, times(1)).create(captor.capture());

        EmailStaging capturedStaging = captor.getValue();
        assertEquals(Template.VERIFY_ATTEMPT.name(), capturedStaging.getEmailTemplateName());
    }

    private User createUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(USER_EMAIL);
        user.setUsername("testuser");
        return user;
    }

    private Client createClient() {
        Client client = new Client();
        client.setId(CLIENT_ID);
        client.setClientId("client-name");
        return client;
    }

    private EmailStaging createEmailStaging() {
        EmailStaging staging = new EmailStaging();
        staging.setId("staging-id-123");
        staging.setUserId(USER_ID);
        staging.setApplicationId(CLIENT_ID);
        staging.setReferenceId(DOMAIN_ID);
        staging.setReferenceType(REFERENCE_TYPE);
        staging.setEmailTemplateName(Template.REGISTRATION_CONFIRMATION.template());
        staging.setAttempts(0);
        return staging;
    }
}
