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
import io.gravitee.am.management.handlers.automation.model.AutomationApplication;
import io.gravitee.am.management.handlers.automation.model.AutomationApplicationIdentityProvider;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class ApplicationsResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private static final String APP_KEY = "mobile-app";
    private static final String BROWNFIELD_ID = "11111111-2222-3333-4444-555555555555";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);
    private final String appId = AutomationIds.applicationId(domainId, APP_KEY);

    @BeforeEach
    void stubDomain() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId))).thenReturn(Flowable.empty());
    }

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Application application(String id, String key, ManagedBy managedBy, ApplicationType type) {
        Application app = new Application();
        app.setId(id);
        app.setDomain(domainId);
        app.setAutomationKey(key);
        app.setManagedBy(managedBy);
        app.setName(key);
        app.setType(type);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId("client-1");
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oauth);
        app.setSettings(settings);
        return app;
    }

    private Application existing(ApplicationType type) {
        return application(appId, APP_KEY, ManagedBy.AUTOMATION_API, type);
    }

    private AutomationApplication definition(String key, ApplicationType type) {
        AutomationApplication definition = new AutomationApplication();
        definition.setAutomationKey(key);
        definition.setName("Mobile app");
        definition.setType(type);
        definition.setCertificate("signing-cert");
        return definition;
    }

    private void stubCreate() {
        when(applicationService.create(any(Domain.class), any(Application.class), any()))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(1)));
    }

    private void stubUpdate() {
        when(applicationService.update(any(Domain.class), any(Application.class), any(Application.class), any(), any()))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(2)));
    }

    // --- list ---------------------------------------------------------------

    @Test
    void list_returns_only_automation_managed_sorted() {
        when(applicationService.findByDomain(domainId)).thenReturn(Single.just(Set.of(
                application("id-b", "beta", ManagedBy.AUTOMATION_API, ApplicationType.WEB),
                application("id-legacy", null, null, ApplicationType.WEB),
                application("id-a", "alpha", ManagedBy.AUTOMATION_API, ApplicationType.WEB))));

        Response response = applicationsTarget(DOMAIN_KEY).request().get();

        assertEquals(200, response.getStatus());
        List<AutomationApplication> body = readListEntity(response, AutomationApplication.class);
        assertEquals(List.of("alpha", "beta"), body.stream().map(AutomationApplication::getAutomationKey).toList());
    }

    @Test
    void list_is_forbidden_without_permission() {
        denyPermission();

        Response response = applicationsTarget(DOMAIN_KEY).request().get();

        assertEquals(403, response.getStatus());
        verify(applicationService, never()).findByDomain(any());
    }

    // --- create -------------------------------------------------------------

    @Test
    void put_creates_by_key_with_deterministic_id_and_key_mirrors() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());
        stubCreate();
        AutomationApplication definition = definition(APP_KEY, ApplicationType.NATIVE);
        AutomationApplicationIdentityProvider idp = new AutomationApplicationIdentityProvider();
        idp.setIdentity("customer-users");
        definition.setIdentityProviders(List.of(idp));

        Response response = put(applicationsTarget(DOMAIN_KEY), definition);

        assertEquals(200, response.getStatus());
        assertEquals(APP_KEY, readEntity(response, AutomationApplication.class).getAutomationKey());
        verify(applicationService).create(any(Domain.class), argThat((Application app) -> appId.equals(app.getId())
                && app.isManagedBy(ManagedBy.AUTOMATION_API)
                && APP_KEY.equals(app.getAutomationKey())
                && "signing-cert".equals(app.getCertificateKey())
                && "customer-users".equals(app.getIdentityProviders().first().getIdentityKey())), any());
    }

    @Test
    void put_rejects_a_key_conflicting_with_an_unmanaged_application() {
        when(applicationService.findById(domainId, appId))
                .thenReturn(Maybe.just(application(appId, null, null, ApplicationType.WEB)));

        Response response = put(applicationsTarget(DOMAIN_KEY), definition(APP_KEY, ApplicationType.WEB));

        assertEquals(400, response.getStatus());
        verify(applicationService, never()).create(any(Domain.class), any(Application.class), any());
    }

    @Test
    void put_by_id_does_not_create() {
        when(applicationService.findById(domainId, BROWNFIELD_ID)).thenReturn(Maybe.empty());

        Response response = put(applicationsTarget(DOMAIN_KEY), definition("id:" + BROWNFIELD_ID, ApplicationType.WEB));

        assertEquals(404, response.getStatus());
        verify(applicationService, never()).create(any(Domain.class), any(Application.class), any());
    }

    @Test
    void put_is_forbidden_without_permission() {
        denyPermission();
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());

        Response response = put(applicationsTarget(DOMAIN_KEY), definition(APP_KEY, ApplicationType.WEB));

        assertEquals(403, response.getStatus());
        verify(applicationService, never()).create(any(Domain.class), any(Application.class), any());
    }

    // --- update -------------------------------------------------------------

    @Test
    void put_update_keeps_the_client_id_when_omitted() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(existing(ApplicationType.WEB)));
        stubUpdate();

        Response response = put(applicationsTarget(DOMAIN_KEY), definition(APP_KEY, ApplicationType.WEB));

        assertEquals(200, response.getStatus());
        assertEquals("client-1", readEntity(response, AutomationApplication.class).getClientId());
        verify(applicationService).update(any(Domain.class), any(Application.class),
                argThat(desired -> "client-1".equals(desired.clientId())), any(), any());
    }

    @Test
    void put_update_rejects_a_changed_client_id() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(existing(ApplicationType.WEB)));
        AutomationApplication definition = definition(APP_KEY, ApplicationType.WEB);
        definition.setClientId("client-2");

        Response response = put(applicationsTarget(DOMAIN_KEY), definition);

        assertEquals(400, response.getStatus());
        verify(applicationService, never()).update(any(Domain.class), any(Application.class), any(Application.class), any(), any());
    }

    @Test
    void put_update_applies_the_type_template_on_a_type_change() {
        Application existing = existing(ApplicationType.WEB);
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(existing));
        when(applicationService.changeType(any(Application.class), eq(ApplicationType.SERVICE))).thenAnswer(invocation -> {
            Application changed = new Application(invocation.<Application>getArgument(0));
            changed.setType(ApplicationType.SERVICE);
            return changed;
        });
        stubUpdate();

        Response response = put(applicationsTarget(DOMAIN_KEY), definition(APP_KEY, ApplicationType.SERVICE));

        assertEquals(200, response.getStatus());
        verify(applicationService).changeType(argThat(app -> appId.equals(app.getId())), eq(ApplicationType.SERVICE));
        verify(applicationService).update(any(Domain.class), any(Application.class),
                argThat(desired -> desired.getType() == ApplicationType.SERVICE), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"WEB, AGENT", "AGENT, WEB"})
    void put_update_rejects_a_type_change_to_or_from_agent(ApplicationType from, ApplicationType to) {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(existing(from)));

        Response response = put(applicationsTarget(DOMAIN_KEY), definition(APP_KEY, to));

        assertEquals(400, response.getStatus());
        verify(applicationService, never()).changeType(any(), any());
        verify(applicationService, never()).update(any(Domain.class), any(Application.class), any(Application.class), any(), any());
    }

    @Test
    void put_update_passes_a_token_revocation_processor() {
        Application existing = existing(ApplicationType.WEB);
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(existing));
        stubUpdate();
        when(revokeTokenManagementService.sendProcessRequest(any(Domain.class), any(RevokeToken.class))).thenReturn(Completable.complete());
        AutomationApplication definition = definition(APP_KEY, ApplicationType.WEB);
        definition.setEnabled(false);

        Response response = put(applicationsTarget(DOMAIN_KEY), definition);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<BiFunction<Domain, Application, Completable>> revoke = ArgumentCaptor.forClass(BiFunction.class);
        verify(applicationService).update(any(Domain.class), any(Application.class), any(Application.class), any(), revoke.capture());
        revoke.getValue().apply(domain(), existing).test().assertComplete();
        verify(revokeTokenManagementService).sendProcessRequest(any(Domain.class), any(RevokeToken.class));
    }

    @Test
    void put_by_id_updates_a_brownfield_application() {
        Application brownfield = application(BROWNFIELD_ID, null, null, ApplicationType.WEB);
        when(applicationService.findById(domainId, BROWNFIELD_ID)).thenReturn(Maybe.just(brownfield));
        stubUpdate();

        Response response = put(applicationsTarget(DOMAIN_KEY), definition("id:" + BROWNFIELD_ID, ApplicationType.WEB));

        assertEquals(200, response.getStatus());
        verify(applicationService).update(any(Domain.class), argThat(app -> BROWNFIELD_ID.equals(app.getId())),
                argThat(desired -> desired.getManagedBy() == null && desired.getAutomationKey() == null), any(), any());
    }

    // --- dry-run ------------------------------------------------------------

    @Test
    void dryRun_create_validates_without_creating() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());
        when(applicationService.validateCreate(any(Domain.class), any(Application.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(1)));

        Response response = put(applicationsTarget(DOMAIN_KEY).queryParam("dryRun", true), definition(APP_KEY, ApplicationType.WEB));

        assertEquals(200, response.getStatus());
        assertNull(readEntity(response, AutomationApplication.class).getDryRunErrors());
        verify(applicationService, never()).create(any(Domain.class), any(Application.class), any());
    }

    @Test
    void dryRun_update_validates_without_updating() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(existing(ApplicationType.WEB)));
        when(applicationService.validateUpdate(any(Domain.class), any(Application.class), any(Application.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(2)));

        Response response = put(applicationsTarget(DOMAIN_KEY).queryParam("dryRun", true), definition(APP_KEY, ApplicationType.WEB));

        assertEquals(200, response.getStatus());
        assertNull(readEntity(response, AutomationApplication.class).getDryRunErrors());
        verify(applicationService, never()).update(any(Domain.class), any(Application.class), any(Application.class), any(), any());
    }

    @Test
    void dryRun_reports_validation_errors() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());
        when(applicationService.validateCreate(any(Domain.class), any(Application.class)))
                .thenReturn(Single.error(new InvalidClientMetadataException("non valid scopes")));

        Response response = put(applicationsTarget(DOMAIN_KEY).queryParam("dryRun", true), definition(APP_KEY, ApplicationType.WEB));

        assertEquals(200, response.getStatus());
        AutomationApplication body = readEntity(response, AutomationApplication.class);
        assertEquals(1, body.getDryRunErrors().size());
        assertEquals("non valid scopes", body.getDryRunErrors().get(0).message());
    }
}
