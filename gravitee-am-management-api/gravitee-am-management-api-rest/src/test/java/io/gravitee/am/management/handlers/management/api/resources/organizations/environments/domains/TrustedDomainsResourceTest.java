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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.InvalidTrustDomainException;
import io.gravitee.am.service.model.NewTrustedDomain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
public class TrustedDomainsResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";

    private static final String PEM_CERTIFICATE = "-----BEGIN CERTIFICATE-----\nMIIDGzCCAgOgAwIBAgIUTjn3isOFd6UKhH07F2M9E0+1naMwDQYJKoZIhvcNAQEL\nBQAwHDEaMBgGA1UEAwwRdHJ1c3QtZG9tYWluLXRlc3QwIBcNMjYwODE5MDk0NTU3\nWhgPMjEyNjA3MjYwOTQ1NTdaMBwxGjAYBgNVBAMMEXRydXN0LWRvbWFpbi10ZXN0\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1R3nx/2UCXBoJKsq2K3Z\nPOGsH8Djxj2nnSZ081MVRQL3YgvrSmgzmRSwr6sYx9oJ8Kn32IINUi/aLiwzxlFL\nACK3D/vjWmjP+kwIep64LCzHdD7PaP0ePgN/0jme1pOWVQgyHVs+0q0w6vepHpKu\nl9NcAkMPd7Pq0fUTPg9ebW2sGko7EKZvcsE3/nOA7fStycEtmscLr38wZFGIZSIw\n6QJ8Eww9Rn7CFhTXnE88/LGJ104YQ9taDR7uDQdB42dss2YQ/tWF82LL/PS/9zFW\nzdlF11dI1Xyo3LTTt584PQkDXp0B+wrT4YoOskTK2aMO1IIkV/iNMUDs6fzbtJvY\nAQIDAQABo1MwUTAdBgNVHQ4EFgQUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswHwYDVR0j\nBBgwFoAUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswDwYDVR0TAQH/BAUwAwEB/zANBgkq\nhkiG9w0BAQsFAAOCAQEAn84I8ZiCavT+RBk4f0eHd5pnT9Scj0zcEElwgDw+8nNM\neyTSuPdngL9pQCSBbZbo7ZNCe0WFOf2W/SLIzRx6E3+z1ffAmLr9/LTC1+UNuh1n\nfeEljw97Q0sJKd/EyyUg+ZwUwQ5yiTT8hAQdai1kWhRLbalI2cYYJ+1HlXD73eKM\n9WrINYHmLrXclebEtB3SPTTeGtMZ/ajKlcBV1fu/+OoN5e09hvvEEJZ48aNYZrTt\n2An+LQABjo4LNsjP1prK4PtQl1/PI/jHUiRkHfWZx3y2Rakw5bu+bWMMn3vL4Dsk\nrXiZ9rmdTvbQ38TVI3+G5aBLVP9DS+9cXdHfSlyVPg==\n-----END CERTIFICATE-----";

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

    private static TrustedDomain spiffeTrustDomain(TrustDomainKeyMaterial keyMaterial) {
        return TrustedDomain.builder()
                .id("td-1")
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(DOMAIN_ID)
                .name("example.org")
                .spiffe(SpiffeTrustSettings.builder().spiffeTrustDomain("example.org").build())
                .keyMaterial(keyMaterial)
                .build();
    }

    private static TrustedDomain tokenExchangeTrustDomain() {
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute("emails.value");
        criterion.setExpression("{#token['email']}");
        TrustedDomain td = spiffeTrustDomain(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://issuer.example.org/keys")
                .build());
        td.setSpiffe(null);
        td.setDomainIdentifier("https://issuer.example.org");
        td.setTokenExchange(TokenExchangeTrustSettings.builder()
                .enabled(true)
                .scopeMappings(Map.of("read", "domain:read"))
                .userBindingEnabled(true)
                .userBindingCriteria(List.of(criterion))
                .build());
        return td;
    }

    @Test
    public void shouldExposeTheNestedBlocksAndNoneOfTheDeprecatedAccessors() {
        stubDomain();
        doReturn(Flowable.just(spiffeTrustDomain(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://spire.example.org/keys")
                .build())))
                .when(trustDomainService).findByReference(ReferenceType.DOMAIN, DOMAIN_ID);

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<Map<String, Object>> body = response.readEntity(List.class);
        assertEquals(1, body.size());
        Map<String, Object> trustedDomain = body.get(0);
        assertEquals("example.org", ((Map<String, Object>) trustedDomain.get("spiffe")).get("spiffeTrustDomain"));
        Map<String, Object> keyMaterial = (Map<String, Object>) trustedDomain.get("keyMaterial");
        assertEquals("jwks_url", keyMaterial.get("source"));
        assertEquals("https://spire.example.org/keys", keyMaterial.get("jwksUrl"));
        assertFalse(trustedDomain.containsKey("spiffeTrustDomain"));
        assertFalse(trustedDomain.containsKey("issuer"));
        assertFalse(trustedDomain.containsKey("bundleSource"));
        assertFalse(trustedDomain.containsKey("jwksUrl"));
    }

    @Test
    public void shouldCreateWithPemKeyMaterial() {
        Domain domain = stubDomain();
        doReturn(Single.just(spiffeTrustDomain(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.PEM)
                .certificate(PEM_CERTIFICATE)
                .build())))
                .when(trustDomainService).create(eq(domain), any(NewTrustedDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "spiffe", Map.of("spiffeTrustDomain", "example.org"),
                        "keyMaterial", Map.of("source", "PEM", "certificate", PEM_CERTIFICATE))));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        ArgumentCaptor<NewTrustedDomain> captor = ArgumentCaptor.forClass(NewTrustedDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), any());
        assertEquals(KeyMaterialSource.PEM, captor.getValue().getKeyMaterial().getSource());
        assertEquals(PEM_CERTIFICATE, captor.getValue().getKeyMaterial().getCertificate());
        assertEquals("example.org", captor.getValue().getSpiffe().getSpiffeTrustDomain());
    }

    @Test
    public void shouldCreateWithInlineJwkSet() {
        Domain domain = stubDomain();
        RSAKey key = new RSAKey();
        key.setKid("key-1");
        key.setE("AQAB");
        key.setN("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86z");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(key));
        doReturn(Single.just(spiffeTrustDomain(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWK_SET)
                .jwkSet(jwkSet)
                .build())))
                .when(trustDomainService).create(eq(domain), any(NewTrustedDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "keyMaterial", Map.of("source", "JWK_SET", "jwkSet", Map.of("keys", List.of(Map.of(
                                "kty", "RSA",
                                "kid", "key-1",
                                "e", "AQAB",
                                "n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86z")))))));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        ArgumentCaptor<NewTrustedDomain> captor = ArgumentCaptor.forClass(NewTrustedDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), any());
        JWKSet received = captor.getValue().getKeyMaterial().getJwkSet();
        assertNotNull(received);
        assertEquals(1, received.getKeys().size());
        assertEquals("key-1", received.getKeys().get(0).getKid());
        assertEquals("RSA", received.getKeys().get(0).getKty());
    }

    @Test
    public void shouldCreateTokenExchangeTrustedDomainWithScopeMappingsAndUserBinding() {
        Domain domain = stubDomain();
        doReturn(Single.just(tokenExchangeTrustDomain()))
                .when(trustDomainService).create(eq(domain), any(NewTrustedDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request()
                .post(Entity.json(Map.of(
                        "name", "issuer.example.org",
                        "domainIdentifier", "https://issuer.example.org",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://issuer.example.org/keys"),
                        "tokenExchange", Map.of(
                                "scopeMappings", Map.of("read", "domain:read"),
                                "userBindingEnabled", true,
                                "userBindingCriteria", List.of(Map.of(
                                        "attribute", "emails.value",
                                        "expression", "{#token['email']}"))))));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        ArgumentCaptor<NewTrustedDomain> captor = ArgumentCaptor.forClass(NewTrustedDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), any());
        NewTrustedDomain received = captor.getValue();
        assertEquals("https://issuer.example.org", received.getDomainIdentifier());
        assertEquals(Map.of("read", "domain:read"), received.getTokenExchange().getScopeMappings());
        assertTrue(received.getTokenExchange().isUserBindingEnabled());
        assertEquals(1, received.getTokenExchange().getUserBindingCriteria().size());

        Map<String, Object> body = response.readEntity(Map.class);
        assertEquals("https://issuer.example.org", body.get("domainIdentifier"));
        assertFalse(body.containsKey("issuer"));
    }

    @Test
    public void shouldRejectInvalidKeyMaterial() {
        Domain domain = stubDomain();
        doReturn(Single.error(new InvalidTrustDomainException("keyMaterial.source is required")))
                .when(trustDomainService).create(eq(domain), any(NewTrustedDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request()
                .post(Entity.json(Map.of("name", "example.org")));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldReturnNotFound_whenDomainIsUnknown() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://spire.example.org/keys"))));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(trustDomainService, never()).create(any(), any(NewTrustedDomain.class), any());
    }

    @Test
    public void shouldRejectListWithoutTrustDomainPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldRejectCreateWithoutTrustDomainPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains").path(DOMAIN_ID).path("trusted-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://spire.example.org/keys"))));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).create(any(), any(NewTrustedDomain.class), any());
    }
}
