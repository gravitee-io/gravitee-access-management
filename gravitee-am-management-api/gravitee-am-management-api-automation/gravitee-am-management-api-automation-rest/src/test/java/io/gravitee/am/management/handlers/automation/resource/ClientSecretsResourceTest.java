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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.automation.AutomationJerseySpringTest;
import io.gravitee.am.management.handlers.automation.model.AutomationClientSecret;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.ApplicationConcurrentModificationException;
import io.gravitee.am.service.exception.ClientSecretInvalidException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class ClientSecretsResourceTest extends AutomationJerseySpringTest {

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

    /** A raw body: the value is write-only, so the model never serializes it. */
    private Map<String, String> definition(String key, String value) {
        Map<String, String> definition = new HashMap<>();
        definition.put("key", key);
        definition.put("value", value);
        return definition;
    }

    private void stubApply() {
        when(applicationSecretService.apply(any(Domain.class), any(Application.class), any(ClientSecret.class), any(), any()))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(2)));
    }

    // --- list ---------------------------------------------------------------

    @Test
    void list_returns_only_automation_secrets_sorted() {
        when(applicationService.findById(domainId, appId))
                .thenReturn(Maybe.just(application(automationSecret("beta"), consoleSecret(), automationSecret("alpha"))));

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).request().get();

        assertEquals(200, response.getStatus());
        List<AutomationClientSecret> body = readListEntity(response, AutomationClientSecret.class);
        assertEquals(List.of("alpha", "beta"), body.stream().map(AutomationClientSecret::getAutomationKey).toList());
    }

    @Test
    void list_returns_404_for_an_unknown_application() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).request().get();

        assertEquals(404, response.getStatus());
    }

    // --- create-or-update ---------------------------------------------------

    @Test
    void put_by_key_applies_a_secret_with_a_deterministic_id() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application()));
        stubApply();

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, VALUE));

        assertEquals(200, response.getStatus());
        String body = response.readEntity(String.class);
        assertFalse(body.contains("value"), body);
        verify(applicationSecretService).apply(any(Domain.class), argThat(app -> appId.equals(app.getId())),
                argThat(desired -> secretId.equals(desired.getId())
                        && SECRET_KEY.equals(desired.getName())
                        && SECRET_KEY.equals(desired.getAutomationKey())
                        && desired.isManagedBy(ManagedBy.AUTOMATION_API)),
                eq(VALUE), any());
    }

    @Test
    void put_without_value_is_rejected() {
        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, null));

        assertEquals(400, response.getStatus());
        verify(applicationSecretService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void put_rejects_a_key_longer_than_64_characters() {
        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition("k".repeat(65), VALUE));

        assertEquals(400, response.getStatus());
        verify(applicationSecretService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void put_by_id_applies_to_that_secret_without_managing_it() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(consoleSecret())));
        stubApply();

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition("id:" + CONSOLE_SECRET_ID, VALUE));

        assertEquals(200, response.getStatus());
        assertEquals("id:" + CONSOLE_SECRET_ID, readEntity(response, AutomationClientSecret.class).getAutomationKey());
        verify(applicationSecretService).apply(any(Domain.class), any(Application.class),
                argThat(desired -> CONSOLE_SECRET_ID.equals(desired.getId()) && desired.getManagedBy() == null && desired.getName() == null),
                eq(VALUE), any());
    }

    @Test
    void put_rejects_a_key_conflicting_with_an_unmanaged_secret() {
        ClientSecret squatter = consoleSecret();
        squatter.setId(secretId);
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(squatter)));

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, VALUE));

        assertEquals(400, response.getStatus());
        verify(applicationSecretService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void put_returns_404_for_an_unknown_application() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, VALUE));

        assertEquals(404, response.getStatus());
    }

    @Test
    void put_returns_409_on_a_persistent_concurrent_modification() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application()));
        when(applicationSecretService.apply(any(Domain.class), any(Application.class), any(ClientSecret.class), any(), any()))
                .thenReturn(Single.error(new ApplicationConcurrentModificationException(appId)));

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, VALUE));

        assertEquals(409, response.getStatus());
    }

    @Test
    void put_is_forbidden_without_permission() {
        denyPermission();
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application()));

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, VALUE));

        assertEquals(403, response.getStatus());
        verify(applicationSecretService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void put_asks_for_create_on_a_new_secret() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application()));
        stubApply();

        put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, VALUE));

        verify(permissionService).hasPermission(any(User.class), argThat(acls -> grants(acls, Acl.CREATE) && !grants(acls, Acl.UPDATE)));
    }

    @Test
    void put_asks_for_update_on_an_existing_secret() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(automationSecret(SECRET_KEY))));
        stubApply();

        put(clientSecretsTarget(DOMAIN_KEY, APP_KEY), definition(SECRET_KEY, VALUE));

        verify(permissionService).hasPermission(any(User.class), argThat(acls -> grants(acls, Acl.UPDATE) && !grants(acls, Acl.CREATE)));
    }

    @Test
    void list_is_forbidden_before_any_lookup() {
        denyPermission();

        Response response = clientSecretsTarget(DOMAIN_KEY, APP_KEY).request().get();

        assertEquals(403, response.getStatus());
        verify(applicationService, never()).findById(any(), any());
    }

    /** Whether a member of the environment holding only {@code acl} on client secrets passes {@code acls}. */
    private static boolean grants(PermissionAcls acls, Acl acl) {
        Membership membership = new Membership();
        membership.setReferenceType(ReferenceType.ENVIRONMENT);
        membership.setReferenceId(ENV_ID);
        return acls.match(Map.of(membership, Map.of(Permission.APPLICATION_OPENID, Set.of(acl))));
    }

    // --- dry-run ------------------------------------------------------------

    @Test
    void dryRun_validates_without_applying() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application()));
        when(applicationSecretService.validateApply(any(Domain.class), any(Application.class), any(ClientSecret.class), eq(VALUE)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(2)));

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY).queryParam("dryRun", true), definition(SECRET_KEY, VALUE));

        assertEquals(200, response.getStatus());
        assertNull(readEntity(response, AutomationClientSecret.class).getDryRunErrors());
        verify(applicationSecretService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void dryRun_reports_validation_errors_without_the_value() throws Exception {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application()));
        when(applicationSecretService.validateApply(any(Domain.class), any(Application.class), any(ClientSecret.class), eq(VALUE)))
                .thenReturn(Single.error(new ClientSecretInvalidException("Client secret value is immutable; use a new key to rotate")));

        Response response = put(clientSecretsTarget(DOMAIN_KEY, APP_KEY).queryParam("dryRun", true), definition(SECRET_KEY, VALUE));

        assertEquals(200, response.getStatus());
        String body = response.readEntity(String.class);
        assertFalse(body.contains(VALUE), body);
        assertEquals(1, objectMapper.readValue(body, AutomationClientSecret.class).getDryRunErrors().size());
    }
}
