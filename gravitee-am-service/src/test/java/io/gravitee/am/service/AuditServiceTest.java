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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.service.impl.AuditServiceImpl;
import io.gravitee.am.service.reporter.AuditReporterService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientAuthAuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditReporterService auditReporterService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Environment environment;

    @InjectMocks
    private AuditServiceImpl auditService = new AuditServiceImpl();
    private boolean initialize = false;
    private long completedTasks = 0;

    @BeforeEach
    public void setup() {
        Mockito.when(environment.getProperty("reporters.audits.excluded_audit_types[0]", String.class)).thenReturn(EventType.CLIENT_AUTHENTICATION);
        auditService.afterPropertiesSet();
        completedTasks = ((ThreadPoolExecutor)auditService.getExecutorService()).getCompletedTaskCount();
    }

    @Test
    public void should_publish_audit() {
        auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED));
        while(((ThreadPoolExecutor)auditService.getExecutorService()).getCompletedTaskCount() <= completedTasks) {
            Awaitility.await().during(1, TimeUnit.SECONDS);
        }
        Mockito.verify(auditReporterService).report(Mockito.any());
    }

    @Test
    public void should_filter_audit() {
        auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class).type(EventType.CLIENT_AUTHENTICATION));
        while(((ThreadPoolExecutor)auditService.getExecutorService()).getCompletedTaskCount() <= completedTasks) {
            Awaitility.await().during(1, TimeUnit.SECONDS);
        }
        Mockito.verify(auditReporterService, Mockito.never()).report(Mockito.any());
    }
}
