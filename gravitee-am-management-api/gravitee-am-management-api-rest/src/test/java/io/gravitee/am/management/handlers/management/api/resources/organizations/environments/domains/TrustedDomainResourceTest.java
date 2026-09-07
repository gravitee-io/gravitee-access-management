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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.service.exception.TrustDomainIssuerAlreadyExistsException;
import io.gravitee.am.service.model.UpdateTrustedDomain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
public class TrustedDomainResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String TRUSTED_DOMAIN_ID = "td-1";

    @BeforeEach
    public void resetTrustDomainService() {
        reset(trustDomainService);
    }

    private Domain stubDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        return domain;
    }

    private jakarta.ws.rs.client.WebTarget trustedDomainTarget() {
        return target("domains").path(DOMAIN_ID).path("trusted-domains").path(TRUSTED_DOMAIN_ID);
    }

    private static TrustedDomain tokenExchangeTrustDomain() {
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute("emails.value");
        criterion.setExpression("{#token['email']}");
        return TrustedDomain.builder()
                .id(TRUSTED_DOMAIN_ID)
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(DOMAIN_ID)
                .name("issuer.example.org")
                .keyMaterial(TrustDomainKeyMaterial.builder()
                        .source(KeyMaterialSource.JWKS_URL)
                        .jwksUrl("https://issuer.example.org/keys")
                        .build())
                .domainIdentifier("https://issuer.example.org")
                .tokenExchange(TokenExchangeTrustSettings.builder()
                        .scopeMappings(Map.of("read", "domain:read"))
                        .userBindingEnabled(true)
                        .userBindingCriteria(List.of(criterion))
                        .build())
                .build();
    }

    @Test
    public void shouldReadTheNestedBlocksAndNoneOfTheDeprecatedAccessors() {
        stubDomain();
        doReturn(Maybe.just(tokenExchangeTrustDomain())).when(trustDomainService).findById(TRUSTED_DOMAIN_ID);

        final Response response = trustedDomainTarget().request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        Map<String, Object> body = response.readEntity(Map.class);
        assertEquals("https://issuer.example.org", body.get("domainIdentifier"));
        Map<String, Object> tokenExchange = (Map<String, Object>) body.get("tokenExchange");
        assertEquals(Map.of("read", "domain:read"), tokenExchange.get("scopeMappings"));
        assertEquals(Boolean.TRUE, tokenExchange.get("userBindingEnabled"));
        assertFalse(body.containsKey("issuer"));
        assertFalse(body.containsKey("scopeMappings"));
        assertFalse(body.containsKey("userBindingEnabled"));
        assertFalse(body.containsKey("refreshIntervalSeconds"));
    }

    @Test
    public void shouldReturnNotFound_whenTrustedDomainBelongsToAnotherDomain() {
        stubDomain();
        TrustedDomain other = tokenExchangeTrustDomain();
        other.setReferenceId("another-domain");
        doReturn(Maybe.just(other)).when(trustDomainService).findById(TRUSTED_DOMAIN_ID);

        final Response response = trustedDomainTarget().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldReturnNotFound_whenTrustedDomainIsUnknown() {
        stubDomain();
        doReturn(Maybe.empty()).when(trustDomainService).findById(TRUSTED_DOMAIN_ID);

        final Response response = trustedDomainTarget().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateTokenExchangeSettings() {
        Domain domain = stubDomain();
        doReturn(Single.just(tokenExchangeTrustDomain()))
                .when(trustDomainService).update(eq(domain), eq(TRUSTED_DOMAIN_ID), any(UpdateTrustedDomain.class), any());

        final Response response = trustedDomainTarget().request()
                .put(Entity.json(Map.of(
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://issuer.example.org/v2/keys"),
                        "domainIdentifier", "https://issuer.example.org/v2",
                        "tokenExchange", Map.of(
                                "scopeMappings", Map.of("write", "domain:write"),
                                "userBindingEnabled", true,
                                "userBindingCriteria", List.of(Map.of(
                                        "attribute", "username",
                                        "expression", "{#token['sub']}"))))));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        ArgumentCaptor<UpdateTrustedDomain> captor = ArgumentCaptor.forClass(UpdateTrustedDomain.class);
        verify(trustDomainService).update(eq(domain), eq(TRUSTED_DOMAIN_ID), captor.capture(), any());
        UpdateTrustedDomain received = captor.getValue();
        assertEquals("https://issuer.example.org/v2", received.getDomainIdentifier());
        assertEquals(Map.of("write", "domain:write"), received.getTokenExchange().getScopeMappings());
        assertTrue(received.getTokenExchange().isUserBindingEnabled());
        assertEquals("username", received.getTokenExchange().getUserBindingCriteria().get(0).getAttribute());
    }

    @Test
    public void shouldClearAUsageWhenTheUpdateSendsItBlank() {
        Domain domain = stubDomain();
        doReturn(Single.just(tokenExchangeTrustDomain()))
                .when(trustDomainService).update(eq(domain), eq(TRUSTED_DOMAIN_ID), any(UpdateTrustedDomain.class), any());

        trustedDomainTarget().request()
                .put(Entity.json(Map.of(
                        "domainIdentifier", "",
                        "spiffe", Map.of("spiffeTrustDomain", "acme.org"))));

        ArgumentCaptor<UpdateTrustedDomain> captor = ArgumentCaptor.forClass(UpdateTrustedDomain.class);
        verify(trustDomainService).update(eq(domain), eq(TRUSTED_DOMAIN_ID), captor.capture(), any());
        assertEquals("", captor.getValue().getDomainIdentifier());
        assertEquals("acme.org", captor.getValue().getSpiffe().getSpiffeTrustDomain());
    }

    @Test
    public void shouldRejectUpdateIntroducingDuplicateIssuer() {
        Domain domain = stubDomain();
        doReturn(Single.error(new TrustDomainIssuerAlreadyExistsException("https://issuer.example.org")))
                .when(trustDomainService).update(eq(domain), eq(TRUSTED_DOMAIN_ID), any(UpdateTrustedDomain.class), any());

        final Response response = trustedDomainTarget().request()
                .put(Entity.json(Map.of(
                        "domainIdentifier", "https://issuer.example.org",
                        "tokenExchange", Map.of())));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldDeleteTrustedDomain() {
        Domain domain = stubDomain();
        doReturn(Completable.complete()).when(trustDomainService).delete(eq(domain), eq(TRUSTED_DOMAIN_ID), any());

        final Response response = trustedDomainTarget().request().delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
        verify(trustDomainService).delete(eq(domain), eq(TRUSTED_DOMAIN_ID), any());
    }

    @Test
    public void shouldRejectReadWithoutTrustDomainPermission() {
        stubDomain();
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = trustedDomainTarget().request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldRejectUpdateWithoutTrustDomainPermission() {
        stubDomain();
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = trustedDomainTarget().request()
                .put(Entity.json(Map.of("description", "amended")));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).update(any(), any(), any(UpdateTrustedDomain.class), any());
    }

    @Test
    public void shouldRejectDeleteWithoutTrustDomainPermission() {
        stubDomain();
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = trustedDomainTarget().request().delete();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).delete(any(), any(), any());
    }
}
