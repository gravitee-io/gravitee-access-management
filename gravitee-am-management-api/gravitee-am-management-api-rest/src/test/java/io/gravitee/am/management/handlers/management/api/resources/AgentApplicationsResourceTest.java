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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.AgentSettings;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.model.ReferenceType.APPLICATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

public class AgentApplicationsResourceTest extends JerseySpringTest {

    @Test
    public void shouldListAgentApplications() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application agent = buildAgent("agent-1-id", "agent-1", domainId, AgentType.AUTONOMOUS);
        final Page<Application> agentPage = new Page<>(List.of(agent), 0, 1);

        doReturn(Flowable.just("agent-1-id"))
                .when(permissionService).getReferenceIdsWithPermission(Mockito.any(), eq(APPLICATION), eq(Permission.APPLICATION), eq(Set.of(Acl.READ)));
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(agentPage)).when(applicationService).findAgentsByDomain(domainId, 0, 50);

        final Response response = target("domains").path(domainId).path("applications").path("agents").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map<?, ?> body = readEntity(response, Map.class);
        final List<?> data = (List<?>) body.get("data");
        assertEquals(1, data.size());

        @SuppressWarnings("unchecked")
        final Map<String, Object> item = (Map<String, Object>) data.get(0);
        assertEquals("agent-1-id", item.get("id"));
        assertEquals("agent-1", item.get("name"));
        assertEquals(Boolean.TRUE, item.get("enabled"));

        assertEquals("agent", item.get("type"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> settings = (Map<String, Object>) item.get("settings");
        assertTrue(settings.containsKey("oauth"));
        assertTrue(settings.containsKey("agent"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> oauth = (Map<String, Object>) settings.get("oauth");
        assertEquals("client-agent-1", oauth.get("clientId"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> agentBlock = (Map<String, Object>) settings.get("agent");
        assertEquals(AgentType.AUTONOMOUS.name().toLowerCase(), agentBlock.get("agentType"));
    }

    @Test
    public void shouldSearchAgentApplications() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application agent = buildAgent("agent-1-id", "alpha-agent", domainId, AgentType.USER_EMBEDDED);
        final Page<Application> page = new Page<>(List.of(agent), 0, 1);

        doReturn(Flowable.just("agent-1-id"))
                .when(permissionService).getReferenceIdsWithPermission(Mockito.any(), eq(APPLICATION), eq(Permission.APPLICATION), eq(Set.of(Acl.READ)));
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(page)).when(applicationService).searchAgents(domainId, "alpha*", 0, 50);

        final Response response = target("domains").path(domainId).path("applications").path("agents")
                .queryParam("q", "alpha*").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldReturn500_onTechnicalException() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Flowable.just("agent-1-id"))
                .when(permissionService).getReferenceIdsWithPermission(Mockito.any(), eq(APPLICATION), eq(Permission.APPLICATION), eq(Set.of(Acl.READ)));
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.error(new TechnicalManagementException("boom"))).when(applicationService).findAgentsByDomain(domainId, 0, 50);

        final Response response = target("domains").path(domainId).path("applications").path("agents").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldClampSizeToMax() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Flowable.just("agent-1-id"))
                .when(permissionService).getReferenceIdsWithPermission(Mockito.any(), eq(APPLICATION), eq(Permission.APPLICATION), eq(Set.of(Acl.READ)));
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new Page<Application>(List.of(), 0, 0L)))
                .when(applicationService).findAgentsByDomain(domainId, 0, 100);

        final Response response = target("domains").path(domainId).path("applications").path("agents")
                .queryParam("size", 500).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    private static Application buildAgent(String id, String name, String domainId, AgentType agentType) {
        final Application application = new Application();
        application.setId(id);
        application.setName(name);
        application.setDescription("agent " + name);
        application.setType(ApplicationType.SERVICE);
        application.setEnabled(true);
        application.setDomain(domainId);
        application.setType(io.gravitee.am.model.application.ApplicationType.AGENT);
        application.setUpdatedAt(new Date());

        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId("client-" + name);
        oauth.setRedirectUris(List.of("https://example.com/callback"));

        final AgentSettings agent = new AgentSettings();
        agent.setAgentType(agentType);

        final ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oauth);
        settings.setAgent(agent);
        application.setSettings(settings);
        return application;
    }
}
