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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.management.handlers.automation.AutomationJerseySpringTest;
import io.gravitee.am.management.handlers.automation.model.AutomationClientSecret;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.application.ClientSecret;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class ClientSecretResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private static final String APP_KEY = "mobile-app";
    private static final String SECRET_KEY = "ci-secret";
    private static final String CONSOLE_SECRET_ID = "11111111-2222-3333-4444-555555555555";
    private static final String VALUE = "s".repeat(43);
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);
    private final String appId = AutomationIds.applicationId(domainId, APP_KEY);
    private final String secretId = AutomationIds.clientSecretId(appId, SECRET_KEY);

    @BeforeEach
    void stubDomain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain));
    }

    private Application application(ClientSecret... secrets) {
        Application app = new Application();
        app.setId(appId);
        app.setDomain(domainId);
        app.setAutomationKey(APP_KEY);
        app.setManagedBy(ManagedBy.AUTOMATION_API);
        app.setSecrets(new ArrayList<>(List.of(secrets)));
        return app;
    }

    private ClientSecret automationSecret(String key) {
        ClientSecret secret = new ClientSecret();
        secret.setId(AutomationIds.clientSecretId(appId, key));
        secret.setName(key);
        secret.setAutomationKey(key);
        secret.setManagedBy(ManagedBy.AUTOMATION_API);
        return secret;
    }

    private ClientSecret consoleSecret() {
        ClientSecret secret = new ClientSecret();
        secret.setId(CONSOLE_SECRET_ID);
        secret.setName("Console secret");
        return secret;
    }

    @Test
    void get_returns_an_automation_secret_by_key() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(automationSecret(SECRET_KEY))));

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().get();

        assertEquals(200, response.getStatus());
        assertEquals(SECRET_KEY, readEntity(response, AutomationClientSecret.class).getAutomationKey());
    }

    @Test
    void get_returns_a_console_secret_by_id() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(consoleSecret())));

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path("id:" + CONSOLE_SECRET_ID).request().get();

        assertEquals(200, response.getStatus());
        assertEquals("id:" + CONSOLE_SECRET_ID, readEntity(response, AutomationClientSecret.class).getAutomationKey());
    }

    @Test
    void get_returns_404_for_an_unknown_key() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(consoleSecret())));

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().get();

        assertEquals(404, response.getStatus());
    }

    @Test
    void get_is_forbidden_before_any_lookup() {
        denyPermission();

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().get();

        assertEquals(403, response.getStatus());
        verify(applicationService, never()).findById(any(), any());
    }

    @Test
    void get_returns_404_for_a_key_whose_id_holds_an_unmanaged_secret() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(squatter())));

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().get();

        assertEquals(404, response.getStatus());
    }

    @Test
    void delete_leaves_an_unmanaged_secret_whose_id_matches_the_key() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(squatter())));

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(applicationSecretService, never()).remove(any(), any(), any(), any());
    }

    /** A secret not managed by the Automation API that holds the id the key maps to. */
    private ClientSecret squatter() {
        ClientSecret secret = consoleSecret();
        secret.setId(secretId);
        return secret;
    }

    @Test
    void delete_removes_the_secret() {
        Application application = application(automationSecret(SECRET_KEY));
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application));
        when(applicationSecretService.remove(any(Domain.class), any(Application.class), eq(secretId), any())).thenReturn(Completable.complete());

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(applicationSecretService).remove(any(Domain.class), eq(application), eq(secretId), any());
    }

    @Test
    void delete_of_a_missing_secret_returns_204() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application()));

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(applicationSecretService, never()).remove(any(), any(), any(), any());
    }

    @Test
    void delete_of_a_missing_application_returns_204() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(applicationSecretService, never()).remove(any(), any(), any(), any());
    }

    @Test
    void delete_is_forbidden_before_any_lookup() {
        denyPermission();

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).path(SECRET_KEY).request().delete();

        assertEquals(403, response.getStatus());
        verify(applicationService, never()).findById(any(), any());
    }
}
