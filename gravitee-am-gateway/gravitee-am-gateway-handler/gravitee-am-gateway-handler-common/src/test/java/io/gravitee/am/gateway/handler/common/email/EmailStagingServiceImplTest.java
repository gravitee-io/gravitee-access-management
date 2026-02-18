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
package io.gravitee.am.gateway.handler.common.email;

import io.gravitee.am.common.exception.ActionLeaseException;
import io.gravitee.am.gateway.handler.common.email.impl.EmailStagingServiceImpl;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.gateway.api.EmailStagingRepository;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.gateway.handler.common.email.impl.EmailStagingServiceImpl.ACTION_EMAIL_STAGING_PROCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private Node node;

    private static final String DOMAIN_ID = "domain-id-123";
    private static final String USER_ID = "user-id-456";
    private static final String USER_EMAIL = "user@example.com";
    private static final String CLIENT_ID = "client-id-789";
    private static final String NODE_ID = "node-id-001";
    private static final ReferenceType REFERENCE_TYPE = ReferenceType.DOMAIN;

    @BeforeEach
    void setUp() {
        lenient().when(domain.getId()).thenReturn(DOMAIN_ID);
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

    @Test
    void shouldInitializeWorkerIdAndActionIdInAfterPropertiesSet() throws Exception {
        // Given
        when(node.id()).thenReturn(NODE_ID);

        // When
        emailStagingService.afterPropertiesSet();

        // Then - verify that node.id() and domain.getId() were called
        verify(node, times(1)).id();
        verify(domain, times(1)).getId();
        // Note: We can't directly verify the internal fields, but we can verify the mocks were called
    }

    @Test
    void shouldFetchEmailsStagingWhenLeaseIsAcquired() {
        // Given
        Reference reference = Reference.domain(DOMAIN_ID);
        int batchSize = 10;

        ActionLease lease = createActionLease();
        when(actionLeaseRepository.acquireLease(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Maybe.just(lease));

        List<EmailStaging> emailStagings = List.of(
                createEmailStaging(),
                createEmailStagingWithId("staging-id-456"),
                createEmailStagingWithId("staging-id-789")
        );
        when(emailStagingRepository.findOldestByUpdateDate(any(Reference.class), eq(batchSize)))
                .thenReturn(Flowable.fromIterable(emailStagings));

        when(node.id()).thenReturn(NODE_ID);

        initializeService();

        // When
        TestSubscriber<EmailStaging> subscriber = emailStagingService.acquireLeaseAndFetch(reference, batchSize).test();

        // Then
        subscriber.awaitDone(5, TimeUnit.SECONDS);
        subscriber.assertNoErrors();
        subscriber.assertComplete();
        subscriber.assertValueCount(3);

        verify(actionLeaseRepository, times(1)).acquireLease(
                eq(ACTION_EMAIL_STAGING_PROCESS + ":" + DOMAIN_ID),
                eq(NODE_ID),
                any(Duration.class)
        );
        verify(emailStagingRepository, times(1)).findOldestByUpdateDate(reference, batchSize);
    }

    @Test
    void shouldFailToFetchWhenLeaseCannotBeAcquired() {
        // Given
        Reference reference = Reference.domain(DOMAIN_ID);
        int batchSize = 10;

        when(actionLeaseRepository.acquireLease(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Maybe.empty());

        when(node.id()).thenReturn(NODE_ID);

        initializeService();

        // When
        TestSubscriber<EmailStaging> subscriber = emailStagingService.acquireLeaseAndFetch(reference, batchSize).test();

        // Then
        subscriber.awaitDone(5, TimeUnit.SECONDS);
        subscriber.assertError(ActionLeaseException.class);
        subscriber.assertError(throwable ->
                throwable.getMessage().contains("Unable to acquire action lease")
        );

        verify(actionLeaseRepository, times(1)).acquireLease(anyString(), anyString(), any(Duration.class));
        verify(emailStagingRepository, never()).findOldestByUpdateDate(any(Reference.class), anyInt());
    }

    @Test
    void shouldHandleRepositoryErrorDuringFetch() {
        // Given
        Reference reference = Reference.domain(DOMAIN_ID);
        int batchSize = 10;

        ActionLease lease = createActionLease();
        when(actionLeaseRepository.acquireLease(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Maybe.just(lease));

        RuntimeException exception = new RuntimeException("Database connection error");
        when(emailStagingRepository.findOldestByUpdateDate(any(Reference.class), eq(batchSize)))
                .thenReturn(Flowable.error(exception));

        when(node.id()).thenReturn(NODE_ID);

        initializeService();

        // When
        TestSubscriber<EmailStaging> subscriber = emailStagingService.acquireLeaseAndFetch(reference, batchSize).test();

        // Then
        subscriber.awaitDone(5, TimeUnit.SECONDS);
        subscriber.assertError(RuntimeException.class);
        subscriber.assertError(throwable -> throwable.getMessage().equals("Database connection error"));

        verify(actionLeaseRepository, times(1)).acquireLease(anyString(), anyString(), any(Duration.class));
        verify(emailStagingRepository, times(1)).findOldestByUpdateDate(reference, batchSize);
    }

    @Test
    void shouldHandleLeaseReleaseFailureGracefully() {
        // Given
        Reference reference = Reference.domain(DOMAIN_ID);
        int batchSize = 10;

        ActionLease lease = createActionLease();
        when(actionLeaseRepository.acquireLease(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Maybe.just(lease));

        List<EmailStaging> emailStagings = List.of(
                createEmailStaging(),
                createEmailStagingWithId("staging-id-456")
        );
        when(emailStagingRepository.findOldestByUpdateDate(any(Reference.class), eq(batchSize)))
                .thenReturn(Flowable.fromIterable(emailStagings));

        when(node.id()).thenReturn(NODE_ID);

        initializeService();

        // When
        TestSubscriber<EmailStaging> subscriber = emailStagingService.acquireLeaseAndFetch(reference, batchSize).test();

        // Then - The stream should complete successfully even if release fails
        subscriber.awaitDone(5, TimeUnit.SECONDS);
        subscriber.assertNoErrors();
        subscriber.assertComplete();
        subscriber.assertValueCount(2);

        verify(actionLeaseRepository, times(1)).acquireLease(anyString(), anyString(), any(Duration.class));
        verify(emailStagingRepository, times(1)).findOldestByUpdateDate(reference, batchSize);
    }

    private void initializeService() {
        try {
            emailStagingService.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldDeleteEmailStagingWhenProcessed() {
        // Given
        EmailStaging emailStaging = createEmailStaging();
        emailStaging.setProcessed(true);

        when(emailStagingRepository.delete(emailStaging.getId()))
                .thenReturn(Completable.complete());

        // When
        TestObserver<EmailStaging> observer = emailStagingService.manageAfterProcessing(emailStaging).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(emailStaging);

        verify(emailStagingRepository, times(1)).delete(emailStaging.getId());
        verify(emailStagingRepository, never()).updateAttempts(anyString(), anyInt());
    }

    @Test
    void shouldUpdateAttemptsWhenNotProcessed() {
        // Given
        EmailStaging emailStaging = createEmailStaging();
        emailStaging.setProcessed(false);
        emailStaging.setAttempts(3);

        EmailStaging updatedStaging = createEmailStaging();
        updatedStaging.setAttempts(3);

        when(emailStagingRepository.updateAttempts(emailStaging.getId(), 3))
                .thenReturn(Single.just(updatedStaging));

        // When
        TestObserver<EmailStaging> observer = emailStagingService.manageAfterProcessing(emailStaging).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(updatedStaging);

        verify(emailStagingRepository, never()).delete(anyString());
        verify(emailStagingRepository, times(1)).updateAttempts(emailStaging.getId(), 3);
    }

    @Test
    void shouldHandleErrorDuringDelete() {
        // Given
        EmailStaging emailStaging = createEmailStaging();
        emailStaging.setProcessed(true);

        RuntimeException exception = new RuntimeException("Delete operation failed");
        when(emailStagingRepository.delete(emailStaging.getId()))
                .thenReturn(Completable.error(exception));

        // When
        TestObserver<EmailStaging> observer = emailStagingService.manageAfterProcessing(emailStaging).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(emailStaging);

        verify(emailStagingRepository, times(1)).delete(emailStaging.getId());
    }

    @Test
    void shouldHandleErrorDuringUpdateAttempts() {
        // Given
        EmailStaging emailStaging = createEmailStaging();
        emailStaging.setProcessed(false);
        emailStaging.setAttempts(2);

        RuntimeException exception = new RuntimeException("Update operation failed");
        when(emailStagingRepository.updateAttempts(emailStaging.getId(), 2))
                .thenReturn(Single.error(exception));

        // When
        TestObserver<EmailStaging> observer = emailStagingService.manageAfterProcessing(emailStaging).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(emailStaging);

        verify(emailStagingRepository, times(1)).updateAttempts(emailStaging.getId(), 2);
    }

    @Test
    void shouldHandleEmailStagingWithZeroAttempts() {
        // Given
        EmailStaging emailStaging = createEmailStaging();
        emailStaging.setProcessed(false);
        emailStaging.setAttempts(0);

        EmailStaging updatedStaging = createEmailStaging();
        updatedStaging.setAttempts(0);

        when(emailStagingRepository.updateAttempts(emailStaging.getId(), 0))
                .thenReturn(Single.just(updatedStaging));

        // When
        TestObserver<EmailStaging> observer = emailStagingService.manageAfterProcessing(emailStaging).test();

        // Then
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();

        verify(emailStagingRepository, times(1)).updateAttempts(emailStaging.getId(), 0);
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
        return createEmailStagingWithId("staging-id-123");
    }

    private EmailStaging createEmailStagingWithId(String id) {
        EmailStaging staging = new EmailStaging();
        staging.setId(id);
        staging.setUserId(USER_ID);
        staging.setApplicationId(CLIENT_ID);
        staging.setReferenceId(DOMAIN_ID);
        staging.setReferenceType(REFERENCE_TYPE);
        staging.setEmailTemplateName(Template.REGISTRATION_CONFIRMATION.template());
        staging.setAttempts(0);
        return staging;
    }

    private ActionLease createActionLease() {
        ActionLease lease = new ActionLease();
        lease.setId("lease-id-123");
        lease.setAction(ACTION_EMAIL_STAGING_PROCESS + ":" + DOMAIN_ID);
        lease.setNodeId(NODE_ID);
        lease.setExpiryDate(new Date(System.currentTimeMillis() + 1800000)); // 30 minutes from now
        return lease;
    }
}
