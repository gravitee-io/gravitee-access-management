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
 import org.junit.jupiter.params.ParameterizedClass;
 import org.junit.jupiter.params.provider.ValueSource;
 import org.mockito.InjectMocks;
 import org.mockito.Mock;
 import org.mockito.Mockito;
 import org.mockito.Spy;
 import org.mockito.junit.jupiter.MockitoExtension;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.core.env.Environment;
 
 import java.util.concurrent.ThreadPoolExecutor;
 import java.util.concurrent.TimeUnit;
 
 import static io.gravitee.am.service.impl.AuditServiceImpl.PROPERTY_AUDITS_EXCLUDE_CLIENT_AUTH_SUCCESS;
 
 /**
  * @author Eric LELEU (eric.leleu at graviteesource.com)
  * @author GraviteeSource Team
  */
 @ExtendWith(MockitoExtension.class)
 @ParameterizedClass
 @ValueSource(booleans = { true, false })
 public class AuditServiceTest {
 
     @Mock
     private AuditReporterService auditReporterService;
 
     @Spy
     private ObjectMapper objectMapper = new ObjectMapper();
 
     @Mock
     private Environment environment;
 
     @InjectMocks
     private AuditServiceImpl auditService = new AuditServiceImpl();
 
     private long completedTasks = 0;
 
     private boolean excludeClientAuthSuccess;
 
     public AuditServiceTest(boolean excludeClientAuthSuccess) {
         this.excludeClientAuthSuccess = excludeClientAuthSuccess;
     }
 
     @BeforeEach
     public void setup() {
        Mockito.when(environment.getProperty("reporters.audits.excluded_audit_types[0]", String.class)).thenReturn(EventType.TOKEN_CREATED);
        Mockito.when(environment.getProperty(PROPERTY_AUDITS_EXCLUDE_CLIENT_AUTH_SUCCESS, Boolean.class, Boolean.FALSE)).thenReturn(excludeClientAuthSuccess);
         auditService.afterPropertiesSet();
         completedTasks = ((ThreadPoolExecutor)auditService.getExecutorService()).getCompletedTaskCount();
     }
 
     @Test
     public void should_publish_audit() {
         auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED));
         auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).throwable(new RuntimeException("User creation failed")));
         waitForExecutorTasks(2);
         Mockito.verify(auditReporterService, Mockito.times(2)).report(Mockito.any());
     }
 
     @Test
     public void should_publish_audit_client_auth_success() {
         auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class).type(EventType.CLIENT_AUTHENTICATION));
         waitForExecutorTasks(1);
         Mockito.verify(auditReporterService, excludeClientAuthSuccess ? Mockito.never() : Mockito.times(1)).report(Mockito.any());
     }
 
     @Test
     public void should_publish_audit_client_auth_failure() {
         auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class).type(EventType.CLIENT_AUTHENTICATION).throwable(new RuntimeException("Client authentication failed")));
         waitForExecutorTasks(1);
         Mockito.verify(auditReporterService).report(Mockito.any());
     }
 
     @Test
     public void should_filter_audit() {
         auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class).type(EventType.TOKEN_CREATED));
         waitForExecutorTasks(1);
         Mockito.verify(auditReporterService, Mockito.never()).report(Mockito.any());
     }
 
     private void waitForExecutorTasks(long expectedIncrement) {
         final var executor = (ThreadPoolExecutor) auditService.getExecutorService();
         Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> executor.getCompletedTaskCount() >= completedTasks + expectedIncrement);
     }
 }
 