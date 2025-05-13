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

import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import org.junit.Test;

import java.util.List;

public class LoggerNotificationDefinitionFactoryTest {

    @Test
    public void definition_factory_test_client_secret(){
        var notifierSettings = new NotifierSettings(true, Template.CLIENT_SECRET_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new LoggerNotificationDefinitionFactory<>(notifierSettings, obj -> "id=" + obj.getResourceId());

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("id");

        Application application = new Application();
        application.setId("id");
        Domain domain = new Domain();
        domain.setId("domain");
        User user = new User();
        user.setId("user");
        var sub = new ClientSecretNotifierSubject(clientSecret, new Application(), domain, user);

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertValue(def ->
                        def.getResourceId().equals("id") &&
                                def.getData().get("msg").equals("id=id") &&
                                def.getType().equals("log-notifier") &&
                                def.getResourceType().equals("application/secret"));
    }

    @Test
    public void definition_factory_test_certificate(){
        var notifierSettings = new NotifierSettings(true, Template.CLIENT_SECRET_EXPIRATION, "* * * * *", List.of(20,15), "subject");
        var factory = new LoggerNotificationDefinitionFactory<>(notifierSettings, obj -> "id=" + obj.getResourceId());
        Certificate cert = new Certificate();
        cert.setId("id");

        Domain domain = new Domain();
        User user = new User();
        var sub = new CertificateNotifierSubject(cert, domain, user);

        factory.buildNotificationDefinition(sub).test()
                .assertComplete()
                .assertValue(def ->
                        def.getResourceId().equals("id") &&
                                def.getData().get("msg").equals("id=id") &&
                                def.getType().equals("log-notifier") &&
                                def.getResourceType().equals("certificate"));
    }

}