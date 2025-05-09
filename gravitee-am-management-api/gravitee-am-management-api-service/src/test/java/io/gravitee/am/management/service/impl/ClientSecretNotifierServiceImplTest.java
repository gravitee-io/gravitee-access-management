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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.NotifierSettings;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;

public class ClientSecretNotifierServiceImplTest {

    @Spy
    private NotifierSettings certificateNotifierSettings = new NotifierSettings(true, "* * * *", List.of(20, 15), "");

    @Mock
    private NotifierService notifierService;

    @Mock
    private DomainService domainService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private DomainOwnersProvider domainOwnersProvider;

    @Spy
    private EmailNotifierConfiguration emailConfiguration = new EmailNotifierConfiguration();

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ClientSecretNotifierServiceImpl service;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service.uiNotifierEnabled = true;
        service.isLogNotifierEnabled = true;
        service.emailNotifierEnabled = true;
    }

    @Test
    public void shouldNotifyDomainOwnersAboutClientSecretExpiration(){
        Domain domain = new Domain();
        domain.setId("domainId");

        Application application = new Application();
        application.setDomain("domainId");
        application.setId("id");

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("clientSecretId");

        User user = new User();
        user.setId("userId");
        user.setEmail("email@email.com");

        Mockito.when(domainService.findById("domainId")).thenReturn(Maybe.just(domain));
        Mockito.when(domainOwnersProvider.retrieveDomainOwners(eq(domain))).thenReturn(Flowable.just(user));
        Mockito.when(emailService.getFinalEmail(eq(domain), isNull(), eq(Template.CLIENT_SECRET_EXPIRATION), eq(user), any()))
                        .thenReturn(Maybe.just(new Email()));

        service.registerClientSecretExpiration(application,clientSecret)
                .test()
                .assertComplete();

        Mockito.verify(notifierService, times(3)).register(argThat(def -> def.getResourceId().equals("clientSecretId")), any(), any());

    }

    @Test
    public void shouldUnregisterClientSecretNotification(){
        service.unregisterClientSecretExpiration("clientSecretId")
                .test()
                .assertComplete();

        Mockito.verify(notifierService).unregisterAll("clientSecretId", "application/secret");
    }

    @Test
    public void shouldRemoveAcksForClientSecretNotification(){
        Mockito.when(notifierService.deleteAcknowledge("clientSecretId", "application/secret")).thenReturn(Completable.complete());
        service.deleteClientSecretExpirationAcknowledgement("clientSecretId")
                .test()
                .assertComplete();
    }

}