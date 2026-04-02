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

package io.gravitee.am.management.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.management.service.impl.RevokeTokenManagementServiceImpl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class RevokeTokenManagementServiceTest {

    @InjectMocks
    private RevokeTokenManagementService tokenService = new RevokeTokenManagementServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private io.gravitee.am.service.AuditService auditService;

    @Test
    public void shouldSendProcessRequestByUser() {
        var eventCaptor = ArgumentCaptor.forClass(Event.class);
        var auditCaptor = ArgumentCaptor.forClass(ClientTokenAuditBuilder.class);
        when(eventService.create(eventCaptor.capture(), any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));

        var domain = new Domain(UUID.randomUUID().toString());
        domain.setReferenceId(UUID.randomUUID().toString());
        var request = RevokeToken.byUser(domain, "userId", "user-name", "adminId", "admin-name");

        TestObserver<Void> testObserver = tokenService.sendProcessRequest(domain, request).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        Assertions.assertEquals(Type.REVOKE_TOKEN, eventCaptor.getValue().getType());
        Assertions.assertEquals(ReferenceType.DOMAIN, eventCaptor.getValue().getPayload().getReferenceType());
        Assertions.assertEquals(domain.getId(), eventCaptor.getValue().getPayload().getReferenceId());
        var revokeToken = eventCaptor.getValue().getPayload().getRevokeToken();
        Assertions.assertNotNull(revokeToken);
        Assertions.assertEquals(domain.getId(), revokeToken.getDomainId());
        Assertions.assertNotNull(revokeToken.getUser());
        Assertions.assertEquals("userId", revokeToken.getUser().getUserId());
        Assertions.assertNotNull(revokeToken.getPrincipal());
        Assertions.assertEquals("adminId", revokeToken.getPrincipal().getUserId());

        Assertions.assertNotNull(revokeToken.getUserId());
        Assertions.assertEquals("userId", revokeToken.getUserId().id());
        Assertions.assertNull(revokeToken.getClientId());

        verify(auditService, times(1)).report(auditCaptor.capture());
        var audit = auditCaptor.getValue().build(new ObjectMapper());
        Assertions.assertEquals(EventType.TOKEN_REVOKE_SCHEDULED, audit.getType());
        Assertions.assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        Assertions.assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        Assertions.assertEquals(domain.getId(), audit.getReferenceId());
        Assertions.assertNotNull(audit.getActor());
        Assertions.assertEquals("adminId", audit.getActor().getId());
        Assertions.assertNotNull(audit.getTarget());
        Assertions.assertEquals("userId", audit.getTarget().getId());

    }

    @Test
    public void shouldSendProcessRequestByApplication() {
        var eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventService.create(eventCaptor.capture(), any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));

        var domain = new Domain(UUID.randomUUID().toString());
        domain.setReferenceId(UUID.randomUUID().toString());
        var app = new Application();
        app.setId(UUID.randomUUID().toString());
        app.setName("my-app");
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setClientId(UUID.randomUUID().toString());
        applicationSettings.setOauth(oAuthSettings);
        app.setSettings(applicationSettings);
        app.setDomain(domain.getId());
        var request = RevokeToken.byApplication(domain, app, "adminId", "admin-name");

        TestObserver<Void> testObserver = tokenService.sendProcessRequest(domain, request).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        Assertions.assertEquals(Type.REVOKE_TOKEN, eventCaptor.getValue().getType());
        Assertions.assertEquals(ReferenceType.DOMAIN, eventCaptor.getValue().getPayload().getReferenceType());
        Assertions.assertEquals(domain.getId(), eventCaptor.getValue().getPayload().getReferenceId());
        var revokeToken = eventCaptor.getValue().getPayload().getRevokeToken();
        Assertions.assertNotNull(revokeToken);
        Assertions.assertEquals(domain.getId(), revokeToken.getDomainId());
        Assertions.assertNotNull(revokeToken.getApplication());
        Assertions.assertEquals(oAuthSettings.getClientId(), revokeToken.getApplication().getClientId());
        Assertions.assertEquals(app.getId(), revokeToken.getApplication().getApplicationId());
        Assertions.assertNotNull(revokeToken.getPrincipal());
        Assertions.assertEquals("adminId", revokeToken.getPrincipal().getUserId());

        Assertions.assertEquals(oAuthSettings.getClientId(), revokeToken.getClientId());
        Assertions.assertNull(revokeToken.getUserId());
    }

    @Test
    public void shouldSendProcessRequestByUserAndClient() {
        var eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventService.create(eventCaptor.capture(), any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));

        var domain = new Domain(UUID.randomUUID().toString());
        domain.setReferenceId(UUID.randomUUID().toString());
        var clientId = UUID.randomUUID().toString();
        var request = RevokeToken.byUserAndClientId(domain, clientId, "userId", "user-name", "adminId", "admin-name");

        TestObserver<Void> testObserver = tokenService.sendProcessRequest(domain, request).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        Assertions.assertEquals(Type.REVOKE_TOKEN, eventCaptor.getValue().getType());
        Assertions.assertEquals(ReferenceType.DOMAIN, eventCaptor.getValue().getPayload().getReferenceType());
        Assertions.assertEquals(domain.getId(), eventCaptor.getValue().getPayload().getReferenceId());
        var revokeToken = eventCaptor.getValue().getPayload().getRevokeToken();
        Assertions.assertNotNull(revokeToken);
        Assertions.assertEquals(domain.getId(), revokeToken.getDomainId());
        Assertions.assertNotNull(revokeToken.getUser());
        Assertions.assertEquals("userId", revokeToken.getUser().getUserId());
        Assertions.assertNotNull(revokeToken.getApplication());
        Assertions.assertEquals(clientId, revokeToken.getApplication().getClientId());
        Assertions.assertNotNull(revokeToken.getPrincipal());
        Assertions.assertEquals("adminId", revokeToken.getPrincipal().getUserId());

        Assertions.assertNotNull(revokeToken.getUserId());
        Assertions.assertEquals("userId", revokeToken.getUserId().id());
        Assertions.assertEquals(clientId, revokeToken.getClientId());
    }
}
