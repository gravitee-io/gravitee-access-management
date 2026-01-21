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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.safe.ApplicationProperties;
import io.gravitee.am.model.safe.ClientSecretProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import org.junit.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientSecretNotifierSubjectTest {

    @Test
    public void metadata_test(){
        // when
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("clientSecret");

        Application application = new Application();
        application.setId("application");

        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user");

        ClientSecretNotifierSubject sub = new ClientSecretNotifierSubject(clientSecret, application, domain, user);

        // expect
        Map<String, Object> metadata = sub.getMetadata();
        assertEquals(clientSecret.getId(), metadata.get("clientSecretId"));
        assertEquals(domain.getId(), metadata.get("domainId"));
        assertEquals(user.getId(), metadata.get("domainOwner"));
    }

    @Test
    public void data_test(){
        // when
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("clientSecret");
        clientSecret.setName("clientSecret");

        Application application = new Application();
        application.setId("application");

        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user");

        ClientSecretNotifierSubject sub = new ClientSecretNotifierSubject(clientSecret, application, domain, user);

        // expect
        Map<String, Object> metadata = sub.getData();
        var clientSecretProperties = (ClientSecretProperties) metadata.get("clientSecret");
        var domainProperties = (DomainProperties) metadata.get("domain");
        var userProperties = (UserProperties) metadata.get("user");

        assertEquals(clientSecret.getName(),clientSecretProperties.getSecretName());
        assertEquals(userProperties.getId(), user.getId());
        assertEquals(domainProperties.getId(), domain.getId());
        assertEquals("application", metadata.get("resourceType"));
    }

    @Test
    public void data_protected_resource_as_app_test(){
        // when
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("clientSecret");
        clientSecret.setName("clientSecret");

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId("protectedResource");
        protectedResource.setName("PR Name");

        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user");

        ClientSecretNotifierSubject sub = new ClientSecretNotifierSubject(clientSecret, protectedResource, domain, user);

        // expect
        Map<String, Object> metadata = sub.getData();
        var clientSecretProperties = (ClientSecretProperties) metadata.get("clientSecret");
        var domainProperties = (DomainProperties) metadata.get("domain");
        var userProperties = (UserProperties) metadata.get("user");
        var applicationProperties = (ApplicationProperties) metadata.get("application");

        assertEquals(clientSecret.getName(),clientSecretProperties.getSecretName());
        assertEquals(userProperties.getId(), user.getId());
        assertEquals(domainProperties.getId(), domain.getId());
        assertEquals(applicationProperties.getName(), protectedResource.getName());
        assertEquals("protected resource", metadata.get("resourceType"));
    }

}