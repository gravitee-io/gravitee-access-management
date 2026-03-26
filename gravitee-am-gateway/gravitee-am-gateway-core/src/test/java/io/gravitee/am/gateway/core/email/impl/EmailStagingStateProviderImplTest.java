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
package io.gravitee.am.gateway.core.email.impl;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.gateway.api.EmailStagingRepository;
import io.reactivex.rxjava3.core.Flowable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class EmailStagingStateProviderImplTest {

    @Mock
    private EmailStagingRepository emailStagingRepository;

    @InjectMocks
    private EmailStagingStateProviderImpl provider;

    @BeforeEach
    void setup() {
        // use a long period so the scheduler does not re-trigger during a test
        ReflectionTestUtils.setField(provider, "period", 3600);
    }

    @AfterEach
    void teardown() throws Exception {
        provider.destroy();
    }

    // -------------------------------------------------------------------------
    // afterPropertiesSet — scheduler start conditions
    // -------------------------------------------------------------------------

    @Test
    void shouldNotStartScheduler_whenEmailDisabled() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", false);
        ReflectionTestUtils.setField(provider, "bulkEnabled", true);

        provider.afterPropertiesSet();

        verify(emailStagingRepository, never()).listReferences();
    }

    @Test
    void shouldNotStartScheduler_whenBulkDisabled() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", true);
        ReflectionTestUtils.setField(provider, "bulkEnabled", false);

        provider.afterPropertiesSet();

        verify(emailStagingRepository, never()).listReferences();
    }

    @Test
    void shouldNotStartScheduler_whenBothDisabled() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", false);
        ReflectionTestUtils.setField(provider, "bulkEnabled", false);

        provider.afterPropertiesSet();

        verify(emailStagingRepository, never()).listReferences();
    }

    // -------------------------------------------------------------------------
    // hasEmailsToProcess — default state
    // -------------------------------------------------------------------------

    @Test
    void hasEmailsToProcess_returnsFalse_whenMapIsEmpty() {
        assertThat(provider.hasEmailsToProcess("any-domain")).isFalse();
    }

    // -------------------------------------------------------------------------
    // refresh — DOMAIN references populate the map
    // -------------------------------------------------------------------------

    @Test
    void shouldPopulateDomainReferences_afterSchedulerTick() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", true);
        ReflectionTestUtils.setField(provider, "bulkEnabled", true);

        when(emailStagingRepository.listReferences()).thenReturn(
                Flowable.just(
                        Reference.domain("domain-1"),
                        Reference.domain("domain-2")
                )
        );

        provider.afterPropertiesSet();

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> provider.hasEmailsToProcess("domain-1"));

        assertThat(provider.hasEmailsToProcess("domain-1")).isTrue();
        assertThat(provider.hasEmailsToProcess("domain-2")).isTrue();
        assertThat(provider.hasEmailsToProcess("domain-unknown")).isFalse();
    }

    @Test
    void shouldFilterOutNonDomainReferences() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", true);
        ReflectionTestUtils.setField(provider, "bulkEnabled", true);

        when(emailStagingRepository.listReferences()).thenReturn(
                Flowable.just(
                        Reference.domain("domain-1"),
                        new Reference(ReferenceType.ORGANIZATION, "org-1"),
                        new Reference(ReferenceType.ENVIRONMENT, "env-1")
                )
        );

        provider.afterPropertiesSet();

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> provider.hasEmailsToProcess("domain-1"));

        assertThat(provider.hasEmailsToProcess("domain-1")).isTrue();
        assertThat(provider.hasEmailsToProcess("org-1")).isFalse();
        assertThat(provider.hasEmailsToProcess("env-1")).isFalse();
    }

    @Test
    void shouldRemoveStaleDomains_onSubsequentRefresh() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", true);
        ReflectionTestUtils.setField(provider, "bulkEnabled", true);
        // use a very short period so the second tick fires quickly
        ReflectionTestUtils.setField(provider, "period", 1);

        when(emailStagingRepository.listReferences())
                .thenReturn(Flowable.just(Reference.domain("domain-1"), Reference.domain("domain-2")))
                .thenReturn(Flowable.just(Reference.domain("domain-2")));

        provider.afterPropertiesSet();

        // wait for first refresh: both domains present
        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> provider.hasEmailsToProcess("domain-1"));

        // wait for second refresh: domain-1 is gone
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .until(() -> !provider.hasEmailsToProcess("domain-1"));

        assertThat(provider.hasEmailsToProcess("domain-1")).isFalse();
        assertThat(provider.hasEmailsToProcess("domain-2")).isTrue();
    }

    @Test
    void shouldHandleEmptyRepository() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", true);
        ReflectionTestUtils.setField(provider, "bulkEnabled", true);

        when(emailStagingRepository.listReferences()).thenReturn(Flowable.empty());

        provider.afterPropertiesSet();

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> {
                    verify(emailStagingRepository).listReferences();
                    return true;
                });

        assertThat(provider.hasEmailsToProcess("any-domain")).isFalse();
    }

    // -------------------------------------------------------------------------
    // refresh — error resilience
    // -------------------------------------------------------------------------

    @Test
    void shouldNotCrash_whenRepositoryThrows() throws Exception {
        ReflectionTestUtils.setField(provider, "emailEnabled", true);
        ReflectionTestUtils.setField(provider, "bulkEnabled", true);
        // use a short period so the second tick fires after the error
        ReflectionTestUtils.setField(provider, "period", 1);

        when(emailStagingRepository.listReferences())
                .thenReturn(Flowable.error(new RuntimeException("DB unavailable")))
                .thenReturn(Flowable.just(Reference.domain("domain-1")));

        assertThatCode(() -> provider.afterPropertiesSet()).doesNotThrowAnyException();

        // scheduler must survive the error and process the next tick successfully
        Awaitility.await().atMost(4, TimeUnit.SECONDS)
                .until(() -> provider.hasEmailsToProcess("domain-1"));

        assertThat(provider.hasEmailsToProcess("domain-1")).isTrue();
    }

    // -------------------------------------------------------------------------
    // destroy
    // -------------------------------------------------------------------------

    @Test
    void destroy_doesNotThrow_whenSchedulerWasNeverStarted() {
        assertThatCode(() -> provider.destroy()).doesNotThrowAnyException();
    }
}
