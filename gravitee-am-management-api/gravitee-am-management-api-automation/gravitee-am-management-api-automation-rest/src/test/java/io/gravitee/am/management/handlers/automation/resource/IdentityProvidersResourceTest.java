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
import io.gravitee.am.management.handlers.automation.model.AutomationIdentityProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class IdentityProvidersResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private IdentityProvider idp(String id, String key, ManagedBy managedBy) {
        return idp(id, key, managedBy, false);
    }

    private IdentityProvider idp(String id, String key, ManagedBy managedBy, boolean system) {
        IdentityProvider idp = new IdentityProvider();
        idp.setId(id);
        idp.setAutomationKey(key);
        idp.setName(key);
        idp.setType("inline-am-idp");
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId(domainId);
        idp.setManagedBy(managedBy);
        idp.setSystem(system);
        return idp;
    }

    private AutomationIdentityProvider definition(String key) {
        AutomationIdentityProvider in = new AutomationIdentityProvider();
        in.setAutomationKey(key);
        in.setName(key);
        in.setType("inline-am-idp");
        in.setConfiguration("{}");
        return in;
    }

    /**
     * A minimal payload — only {@code key} and {@code system:true} — the AAPI accepts on the system
     * path. {@code name}/{@code type}/{@code configuration} are ignored when {@code system} is true,
     * so this models the way a caller is meant to provision a system IDP.
     */
    private AutomationIdentityProvider systemDefinition(String key) {
        AutomationIdentityProvider in = new AutomationIdentityProvider();
        in.setAutomationKey(key);
        in.setSystem(true);
        return in;
    }

    @Test
    void list_returns_only_automation_managed_sorted() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.just(
                        idp("id-b", "beta", ManagedBy.AUTOMATION_API),
                        idp("id-legacy", "legacy", null),
                        idp("id-a", "alpha", ManagedBy.AUTOMATION_API)));

        Response response = identityProvidersTarget(DOMAIN_KEY).request().get();

        assertEquals(200, response.getStatus());
        List<AutomationIdentityProvider> body = readListEntity(response, AutomationIdentityProvider.class);
        assertEquals(2, body.size());
        assertEquals("alpha", body.get(0).getAutomationKey());
        assertEquals("beta", body.get(1).getAutomationKey());
    }

    @Test
    void put_creates_when_absent() {
        String idpId = AutomationIds.identityProviderId(domainId, "dev-users");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.empty());
        when(identityProviderService.create(any(Domain.class), any(), any(), eq(false)))
                .thenReturn(Single.just(idp(idpId, "dev-users", ManagedBy.AUTOMATION_API)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), definition("dev-users"));

        assertEquals(200, response.getStatus());
        assertEquals("dev-users", readEntity(response, AutomationIdentityProvider.class).getAutomationKey());
    }

    @Test
    void put_rejects_unknown_type() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.empty());
        when(identityProviderManager.checkPluginDeployment(eq("unknown-am-idp")))
                .thenReturn(Completable.error(PluginNotDeployedException.forType("unknown-am-idp")));

        AutomationIdentityProvider def = definition("dev-users");
        def.setType("unknown-am-idp");

        Response response = put(identityProvidersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(identityProviderService, never()).create(any(Domain.class), any(), any(), eq(false));
    }

    @Test
    void put_rejects_configuration_not_matching_schema() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.empty());
        doThrow(InvalidPluginConfigurationException.fromValidationError("not valid"))
                .when(validationService).validate(eq("inline-am-idp"), anyString());

        AutomationIdentityProvider def = definition("dev-users");
        def.setConfiguration("{\"bad\":true}");

        Response response = put(identityProvidersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(identityProviderService, never()).create(any(Domain.class), any(), any(), eq(false));
    }

    @Test
    void put_updates_when_present() {
        String idpId = AutomationIds.identityProviderId(domainId, "dev-users");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.just(idp(idpId, "dev-users", ManagedBy.AUTOMATION_API)));
        when(identityProviderService.update(eq(ReferenceType.DOMAIN), eq(domainId), eq(idpId), any(), any(), eq(false)))
                .thenReturn(Single.just(idp(idpId, "dev-users", ManagedBy.AUTOMATION_API)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), definition("dev-users"));

        assertEquals(200, response.getStatus());
        assertEquals("dev-users", readEntity(response, AutomationIdentityProvider.class).getAutomationKey());
    }

    @Test
    void put_creates_system_from_config() {
        String systemIdpId = AutomationIds.systemIdentityProviderId(domainId);
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.empty());
        when(defaultIdentityProviderService.create(any(Domain.class), eq("sys-idp"), any()))
                .thenReturn(Single.just(idp(systemIdpId, "sys-idp", ManagedBy.AUTOMATION_API, true)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), systemDefinition("sys-idp"));

        assertEquals(200, response.getStatus());
        assertTrue(readEntity(response, AutomationIdentityProvider.class).isSystem());
        // The payload path must never run for a system create.
        verify(identityProviderService, never()).create(any(Domain.class), any(), any(), eq(true));
    }

    @Test
    void put_creates_system_repairs_invalid_domain_reference() {
        // The domain was applied before its system IDP existed, so its registration reference holds the
        // deterministic key-based id; creating the system IDP must rewrite it to the real default-idp id.
        String systemIdpId = AutomationIds.systemIdentityProviderId(domainId);
        String staleId = AutomationIds.identityProviderId(domainId, "sys-idp");
        Domain domain = domain();
        AccountSettings account = new AccountSettings();
        account.setDefaultIdentityProviderForRegistrationKey("sys-idp");
        account.setDefaultIdentityProviderForRegistration(staleId);
        domain.setAccountSettings(account);

        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.empty());
        when(defaultIdentityProviderService.create(any(Domain.class), eq("sys-idp"), any()))
                .thenReturn(Single.just(idp(systemIdpId, "sys-idp", ManagedBy.AUTOMATION_API, true)));
        when(domainService.update(eq(domainId), any(Domain.class), eq(false)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(1)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), systemDefinition("sys-idp"));

        assertEquals(200, response.getStatus());
        ArgumentCaptor<Domain> captor = ArgumentCaptor.forClass(Domain.class);
        verify(domainService).update(eq(domainId), captor.capture(), eq(false));
        assertEquals(systemIdpId,
                captor.getValue().getAccountSettings().getDefaultIdentityProviderForRegistration());
    }

    @Test
    void put_creates_system_leaves_unrelated_domain_reference_untouched() {
        String systemIdpId = AutomationIds.systemIdentityProviderId(domainId);
        Domain domain = domain();
        AccountSettings account = new AccountSettings();
        account.setDefaultIdentityProviderForRegistrationKey("other-idp");
        account.setDefaultIdentityProviderForRegistration(AutomationIds.identityProviderId(domainId, "other-idp"));
        domain.setAccountSettings(account);

        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.empty());
        when(defaultIdentityProviderService.create(any(Domain.class), eq("sys-idp"), any()))
                .thenReturn(Single.just(idp(systemIdpId, "sys-idp", ManagedBy.AUTOMATION_API, true)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), systemDefinition("sys-idp"));

        assertEquals(200, response.getStatus());
        verify(domainService, never()).update(anyString(), any(Domain.class), anyBoolean());
    }

    @Test
    void put_existing_system_is_idempotent_noop() {
        // Re-PUTting a system IDP must not touch either service; gravitee.yaml owns the config.
        String systemIdpId = AutomationIds.systemIdentityProviderId(domainId);
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.just(idp(systemIdpId, "sys-idp", ManagedBy.AUTOMATION_API, true)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), systemDefinition("sys-idp"));

        assertEquals(200, response.getStatus());
        verify(identityProviderService, never())
                .update(eq(ReferenceType.DOMAIN), anyString(), anyString(), any(), any(), anyBoolean());
        verify(identityProviderService, never()).create(any(Domain.class), any(), any(), eq(true));
        verify(defaultIdentityProviderService, never()).create(any(Domain.class), anyString(), any());
    }

    @Test
    void put_rejects_second_system() {
        String existingSystemId = AutomationIds.systemIdentityProviderId(domainId);
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.just(idp(existingSystemId, "primary", ManagedBy.AUTOMATION_API, true)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), systemDefinition("second-system"));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_non_system_missing_required_fields() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.empty());

        AutomationIdentityProvider def = new AutomationIdentityProvider();
        def.setAutomationKey("incomplete");
        // name, type intentionally left null

        Response response = put(identityProvidersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(identityProviderService, never()).create(any(Domain.class), any(), any(), eq(false));
    }

    @Test
    void put_rejects_changing_system_on_existing() {
        String idpId = AutomationIds.identityProviderId(domainId, "dev-users");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        // existing is non-system; the PUT flips system -> rejected (immutable)
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.just(idp(idpId, "dev-users", ManagedBy.AUTOMATION_API)));
        AutomationIdentityProvider def = definition("dev-users");
        def.setSystem(true);

        Response response = put(identityProvidersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_collision_with_non_automation_idp() {
        String idpId = AutomationIds.identityProviderId(domainId, "dev-users");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), eq(domainId)))
                .thenReturn(Flowable.just(idp(idpId, "dev-users", null)));

        Response response = put(identityProvidersTarget(DOMAIN_KEY), definition("dev-users"));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_returns_404_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = put(identityProvidersTarget(DOMAIN_KEY), definition("dev-users"));

        assertEquals(404, response.getStatus());
    }

    @Test
    void put_rejects_invalid_key_pattern() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());

        Response response = put(identityProvidersTarget(DOMAIN_KEY), definition("Dev Users!"));

        assertEquals(400, response.getStatus());
    }
}
