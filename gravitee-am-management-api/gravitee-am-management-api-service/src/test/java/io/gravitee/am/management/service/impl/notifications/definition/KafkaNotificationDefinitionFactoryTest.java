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
import io.gravitee.am.management.service.impl.notifications.KafkaNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.management.service.impl.notifications.template.TemplateProvider;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_KAFKA_NOTIFIER;

public class KafkaNotificationDefinitionFactoryTest {

    KafkaNotifierConfiguration configuration = KafkaNotifierConfiguration.builder().build();
    ObjectMapper mapper = new ObjectMapper();

    TemplateProvider templateProvider = Mockito.mock(TemplateProvider.class);

    @Before
    public void setUp() throws Exception {
        configuration = KafkaNotifierConfiguration.builder()
                .topic("topic")
                .build();
    }

    @Test
    public void definition_factory_test_client_secret(){
        var notifierSettings = new NotifierSettings(true, Template.CLIENT_SECRET_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new KafkaNotificationDefinitionFactory<>(mapper, configuration, templateProvider, notifierSettings);

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("id");

        Application application = new Application();
        application.setId("id");
        Domain domain = new Domain();
        domain.setId("domain");
        User user = new User();
        user.setId("user");
        var sub = new ClientSecretNotifierSubject(clientSecret, application, domain, user);

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertValue(def ->
                        def.getResourceId().equals("id") &&
                                def.getConfiguration().contains("\"topic\":\"topic\"") &&
                                def.getType().equals(TYPE_KAFKA_NOTIFIER) &&
                                def.getResourceType().equals("application/secret"));
    }

    @Test
    public void definition_factory_test_certificate(){
        var notifierSettings = new NotifierSettings(true, Template.CERTIFICATE_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new KafkaNotificationDefinitionFactory<>(mapper, configuration, templateProvider, notifierSettings);

        Certificate cert = new Certificate();
        cert.setId("id");

        Domain domain = new Domain();
        domain.setId("domain");
        User user = new User();
        user.setId("user");
        var sub = new CertificateNotifierSubject(cert, domain, user);

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertValue(def ->
                        def.getResourceId().equals("id") &&
                                def.getConfiguration().contains("\"topic\":\"topic\"") &&
                                def.getType().equals(TYPE_KAFKA_NOTIFIER) &&
                                def.getResourceType().equals("certificate"));
    }

}