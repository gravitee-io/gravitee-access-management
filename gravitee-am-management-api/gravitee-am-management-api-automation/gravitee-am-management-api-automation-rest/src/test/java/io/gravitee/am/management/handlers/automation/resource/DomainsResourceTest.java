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
import io.gravitee.am.management.handlers.automation.model.AutomationDomain;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class DomainsResourceTest extends AutomationJerseySpringTest {

    private Domain domain(String id, String key) {
        Domain domain = new Domain();
        domain.setId(id);
        domain.setAutomationKey(key);
        domain.setHrid(key);
        domain.setName(key);
        domain.setPath("/" + key);
        domain.setReferenceId(ENV_ID);
        domain.setDataPlaneId("default");
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Domain managementDomain(String id, String key) {
        Domain domain = domain(id, key);
        domain.setManagedBy(null);
        return domain;
    }

    private AutomationDomain definition(String key) {
        AutomationDomain in = new AutomationDomain();
        in.setAutomationKey(key);
        in.setName(key);
        in.setPath("/" + key);
        in.setDataPlaneId("default");
        return in;
    }

    @Test
    void list_returns_domains_sorted() {
        when(domainService.findAllByEnvironment(ORG_ID, ENV_ID))
                .thenReturn(Flowable.just(domain("id-b", "beta"), domain("id-a", "alpha")));

        Response response = domainsTarget().request().get();

        assertEquals(200, response.getStatus());
        List<AutomationDomain> body = readListEntity(response, AutomationDomain.class);
        assertEquals(2, body.size());
        assertEquals("alpha", body.get(0).getAutomationKey());
        assertEquals("beta", body.get(1).getAutomationKey());
    }

    @Test
    void put_creates_when_domain_absent() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        Domain created = domain(domainId, "customer-auth");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());
        when(domainService.create(eq(ORG_ID), eq(ENV_ID), any(), any()))
                .thenReturn(Single.just(created));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());
        when(domainService.update(eq(domainId), any(Domain.class), eq(false))).thenReturn(Single.just(created));

        Response response = put(domainsTarget(), definition("customer-auth"));

        assertEquals(200, response.getStatus());
        assertEquals("customer-auth", readEntity(response, AutomationDomain.class).getAutomationKey());
    }

    @Test
    void put_creates_without_data_plane_id_and_lets_the_service_resolve_it() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        Domain created = domain(domainId, "customer-auth");
        AutomationDomain definition = definition("customer-auth");
        definition.setDataPlaneId(null);
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());
        when(domainService.create(eq(ORG_ID), eq(ENV_ID), argThat(newDomain -> newDomain.getDataPlaneId() == null), any()))
                .thenReturn(Single.just(created));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());
        when(domainService.update(eq(domainId), any(Domain.class), eq(false))).thenReturn(Single.just(created));

        Response response = put(domainsTarget(), definition);

        assertEquals(200, response.getStatus());
        assertEquals("customer-auth", readEntity(response, AutomationDomain.class).getAutomationKey());
    }

    @Test
    void put_updates_when_domain_present() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        Domain existing = domain(domainId, "customer-auth");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(existing));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());
        when(domainService.update(eq(domainId), any(Domain.class), eq(false))).thenReturn(Single.just(existing));

        Response response = put(domainsTarget(), definition("customer-auth"));

        assertEquals(200, response.getStatus());
        assertEquals("customer-auth", readEntity(response, AutomationDomain.class).getAutomationKey());
    }

    @Test
    void put_declares_trusted_issuers_as_token_exchange_trusted_domains() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        Domain existing = domain(domainId, "customer-auth");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(existing));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());
        when(domainService.update(eq(domainId), any(Domain.class), eq(false))).thenReturn(Single.just(existing));
        when(trustDomainService.create(eq(existing), any(NewTrustDomain.class), any()))
                .thenReturn(Single.just(new TrustDomain()));

        AutomationDomain in = definition("customer-auth");
        TokenExchangeSettings tokenExchange = new TokenExchangeSettings();
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer("https://issuer.example.com");
        trustedIssuer.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        trustedIssuer.setJwksUri("https://issuer.example.com/keys");
        tokenExchange.setTrustedIssuers(List.of(trustedIssuer));
        in.setTokenExchangeSettings(tokenExchange);

        assertEquals(200, put(domainsTarget(), in).getStatus());

        ArgumentCaptor<NewTrustDomain> captor = ArgumentCaptor.forClass(NewTrustDomain.class);
        verify(trustDomainService).create(eq(existing), captor.capture(), any());
        assertEquals("https://issuer.example.com", captor.getValue().getIssuer());
    }

    @Test
    void put_omitting_trusted_issuers_stops_trusting_them() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        Domain existing = domain(domainId, "customer-auth");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(existing));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());
        when(domainService.update(eq(domainId), any(Domain.class), eq(false))).thenReturn(Single.just(existing));
        when(trustDomainService.findByReference(ReferenceType.DOMAIN, domainId)).thenReturn(Flowable.just(
                TrustDomain.builder()
                        .id("td-1")
                        .name("https-issuer.example.com")
                        .issuer("https://issuer.example.com")
                        .build()));
        when(trustDomainService.delete(eq(existing), anyString(), any())).thenReturn(Completable.complete());

        assertEquals(200, put(domainsTarget(), definition("customer-auth")).getStatus());

        verify(trustDomainService).delete(eq(existing), eq("td-1"), any());
    }

    @Test
    void put_with_id_ref_returns_404_when_domain_does_not_exist() {
        String missingId = "missing-id";
        AutomationDomain idRefDefinition = definition("id:" + missingId);
        when(domainService.findById(eq(missingId))).thenReturn(Maybe.empty());

        Response response = put(domainsTarget(), idRefDefinition);

        assertEquals(404, response.getStatus());
    }

    @Test
    void put_with_id_ref_returns_403_when_update_permission_is_denied() {
        String domainId = "domain-id";
        AutomationDomain idRefDefinition = definition("id:" + domainId);
        Domain existing = domain(domainId, "customer-auth");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(existing));
        denyPermission();

        Response response = put(domainsTarget(), idRefDefinition);

        assertEquals(403, response.getStatus());
    }

    @Test
    void put_is_rejected_when_required_fields_missing() {
        AutomationDomain invalid = new AutomationDomain();
        invalid.setAutomationKey("customer-auth"); // name and path missing

        Response response = put(domainsTarget(), invalid);

        assertEquals(400, response.getStatus());
    }

    @Test
    void list_filters_out_non_automation_managed_domains() {
        Domain managed = domain("id-a", "alpha");
        Domain management = managementDomain("id-b", "beta");
        when(domainService.findAllByEnvironment(ORG_ID, ENV_ID))
                .thenReturn(Flowable.just(managed, management));

        Response response = domainsTarget().request().get();

        assertEquals(200, response.getStatus());
        List<AutomationDomain> body = readListEntity(response, AutomationDomain.class);
        assertEquals(1, body.size());
        assertEquals("alpha", body.get(0).getAutomationKey());
    }

    @Test
    void put_rejects_when_existing_domain_is_not_automation_managed() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        when(domainService.findById(eq(domainId)))
                .thenReturn(Maybe.just(managementDomain(domainId, "customer-auth")));

        Response response = put(domainsTarget(), definition("customer-auth"));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_invalid_key_pattern() {
        AutomationDomain invalid = definition("Customer Auth!"); // uppercase + space + bang

        Response response = put(domainsTarget(), invalid);

        assertEquals(400, response.getStatus());
    }
}
