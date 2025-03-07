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


import io.gravitee.am.common.event.Type;
import io.gravitee.am.management.service.impl.RevokeTokenManagementServiceImpl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
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
    private AuditService auditService;

    @Test
    public void shouldDeleteTokensByUser() {
        var eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventService.create(eventCaptor.capture(), any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));

        var domain = new Domain(UUID.randomUUID().toString());
        var user = new User();
        user.setId("userId");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain.getId());

        TestObserver<Void> testObserver = tokenService.deleteByUser(domain, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any());
        Assertions.assertEquals(Type.REVOKE_TOKEN, eventCaptor.getValue().getType());
        Assertions.assertEquals(ReferenceType.DOMAIN, eventCaptor.getValue().getPayload().getReferenceType());
        Assertions.assertEquals(domain.getId(), eventCaptor.getValue().getPayload().getReferenceId());
        Assertions.assertNotNull(eventCaptor.getValue().getPayload().getRevokeToken());
        Assertions.assertEquals(user.getId(), eventCaptor.getValue().getPayload().getRevokeToken().getUserId().id());
        Assertions.assertEquals(domain.getId(), eventCaptor.getValue().getPayload().getRevokeToken().getDomainId());
    }

    @Test
    public void shouldDeleteTokensByApp() {
        var eventCaptor = ArgumentCaptor.forClass(Event.class);
        when(eventService.create(eventCaptor.capture(), any())).thenAnswer(answer -> Single.just(answer.getArguments()[0]));

        var domain = new Domain(UUID.randomUUID().toString());
        var app = new Application();
        app.setId(UUID.randomUUID().toString());
        ApplicationSettings applicationSettings = new ApplicationSettings();
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setClientId(UUID.randomUUID().toString());
        applicationSettings.setOauth(oAuthSettings);
        app.setSettings(applicationSettings);
        app.setDomain(domain.getId());

        TestObserver<Void> testObserver = tokenService.deleteByApplication(domain, app).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any());
        Assertions.assertEquals(Type.REVOKE_TOKEN, eventCaptor.getValue().getType());
        Assertions.assertEquals(ReferenceType.DOMAIN, eventCaptor.getValue().getPayload().getReferenceType());
        Assertions.assertEquals(domain.getId(), eventCaptor.getValue().getPayload().getReferenceId());
        Assertions.assertNotNull(eventCaptor.getValue().getPayload().getRevokeToken());
        Assertions.assertEquals(oAuthSettings.getClientId(), eventCaptor.getValue().getPayload().getRevokeToken().getClientId());
        Assertions.assertEquals(domain.getId(), eventCaptor.getValue().getPayload().getRevokeToken().getDomainId());
    }
}
