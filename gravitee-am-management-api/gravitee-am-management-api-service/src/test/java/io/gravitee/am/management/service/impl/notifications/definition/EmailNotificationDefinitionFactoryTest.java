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
package io.gravitee.am.management.service.impl.notifications.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_EMAIL_NOTIFIER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

public class EmailNotificationDefinitionFactoryTest {

    EmailService emailService = Mockito.mock(EmailService.class);
    EmailNotifierConfiguration emailConfiguration = new EmailNotifierConfiguration();
    ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        emailConfiguration.setFrom("from@from.com");
    }

    @Test
    public void definition_factory_test_client_secret() {
        NotifierSettings notifierSettings = new NotifierSettings(true, Template.CLIENT_SECRET_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new EmailNotificationDefinitionFactory<>(emailService, emailConfiguration, mapper, notifierSettings);
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("id");

        Application application = new Application();
        application.setId("id");
        Domain domain = new Domain();
        domain.setId("domain");
        User user = new User();
        user.setEmail("email@emai.com");
        user.setId("user");
        var sub = new ClientSecretNotifierSubject(clientSecret, application, domain, user);

        Mockito.when(emailService.getFinalEmail(any(), isNull(), eq(Template.CLIENT_SECRET_EXPIRATION), any(), any()))
                .thenReturn(Maybe.just(new Email()));

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertValue(def ->
                        def.getResourceId().equals("id") &&
                                def.getConfiguration().contains("\"from\":\"from@from.com\"") &&
                                def.getType().equals(TYPE_EMAIL_NOTIFIER) &&
                                def.getResourceType().equals("application/secret"));

    }

    @Test
    public void definition_factory_test_no_template_client_secret() {
        NotifierSettings notifierSettings = new NotifierSettings(true, Template.CLIENT_SECRET_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new EmailNotificationDefinitionFactory<>(emailService, emailConfiguration, mapper, notifierSettings);
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("id");

        Application application = new Application();
        application.setId("id");
        Domain domain = new Domain();
        domain.setId("domain");
        User user = new User();
        user.setId("user");
        var sub = new ClientSecretNotifierSubject(clientSecret, application, domain, user);

        Mockito.when(emailService.getFinalEmail(any(), isNull(), eq(Template.CLIENT_SECRET_EXPIRATION), any(), any()))
                .thenReturn(Maybe.empty());

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void definition_factory_test_certificate() {
        NotifierSettings notifierSettings = new NotifierSettings(true, Template.CERTIFICATE_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new EmailNotificationDefinitionFactory<>(emailService, emailConfiguration, mapper, notifierSettings);
        Certificate cert = new Certificate();
        cert.setId("id");

        Domain domain = new Domain();
        domain.setId("domain");
        User user = new User();
        user.setId("user");
        user.setEmail("email@emai.com");
        var sub = new CertificateNotifierSubject(cert, domain, user);

        Mockito.when(emailService.getFinalEmail(any(), isNull(), eq(Template.CERTIFICATE_EXPIRATION), any(), any()))
                .thenReturn(Maybe.just(new Email()));

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertValue(def ->
                        def.getResourceId().equals("id") &&
                                def.getConfiguration().contains("\"from\":\"from@from.com\"") &&
                                def.getType().equals(TYPE_EMAIL_NOTIFIER) &&
                                def.getResourceType().equals("certificate"));

    }

    @Test
    public void definition_factory_test_no_template_certificate() {
        NotifierSettings notifierSettings = new NotifierSettings(true, Template.CERTIFICATE_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new EmailNotificationDefinitionFactory<>(emailService, emailConfiguration, mapper, notifierSettings);
        Certificate cert = new Certificate();
        cert.setId("id");

        Domain domain = new Domain();
        domain.setId("domain");
        User user = new User();
        user.setId("user");
        var sub = new CertificateNotifierSubject(cert, domain, user);


        Mockito.when(emailService.getFinalEmail(any(), isNull(), eq(Template.CERTIFICATE_EXPIRATION), any(), any()))
                .thenReturn(Maybe.empty());

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertNoValues();
    }

}