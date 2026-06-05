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

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.UpdateReporter;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ReporterServiceImplTest {

    @Mock
    private RepositoriesEnvironment environment;

    @Mock
    private ReporterRepository reporterRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    @Mock
    private PluginConfigurationValidationService validationService;

    @InjectMocks
    private ReporterServiceImpl service;

    private static final String REPORTER_ID = "reporter-id";
    private final Reference reference = Reference.domain("domainId");

    private Reporter existing(String type) {
        return Reporter.builder()
                .id(REPORTER_ID)
                .type(type)
                .configuration("{}")
                .enabled(true)
                .reference(reference)
                .build();
    }

    @Test
    void update_rejects_type_change() {
        when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(existing("reporter-am-kafka")));

        UpdateReporter update = new UpdateReporter();
        update.setName("name");
        update.setType("reporter-am-file");
        update.setConfiguration("{}");

        TestObserver<Reporter> observer = service.update(reference, REPORTER_ID, update, new DefaultUser(), false).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);
        verify(reporterRepository, never()).update(any());
    }

    @Test
    void update_allows_matching_type() {
        when(reporterRepository.findById(REPORTER_ID)).thenReturn(Maybe.just(existing("reporter-am-kafka")));
        when(reporterRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        UpdateReporter update = new UpdateReporter();
        update.setName("name");
        update.setType("reporter-am-kafka");
        update.setConfiguration("{}");

        TestObserver<Reporter> observer = service.update(reference, REPORTER_ID, update, new DefaultUser(), false).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        verify(reporterRepository).update(any());
    }
}
