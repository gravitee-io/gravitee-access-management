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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.model.oidc.TrustDomainTokenExchangeSettings;
import io.gravitee.am.service.exception.InvalidTrustDomainException;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.identityprovider.api.User;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
public class TrustDomainsResourceTest extends JerseySpringTest {

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

    private static TrustDomain trustDomain(TrustDomainKind kind, TrustDomainKeyMaterial keyMaterial) {
        return TrustDomain.builder()
                .id("td-1")
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(DOMAIN_ID)
                .kind(kind)
                .name("example.org")
                .keyMaterial(keyMaterial)
                .refreshIntervalSeconds(300)
                .build();
    }

    @Test
    public void shouldExposeKindAndKeyMaterial() {
        stubDomain();
        doReturn(Flowable.just(trustDomain(TrustDomainKind.SPIFFE, TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://spire.example.org/keys")
                .build())))
                .when(trustDomainService).findByReference(ReferenceType.DOMAIN, DOMAIN_ID);

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<Map<String, Object>> body = response.readEntity(List.class);
        assertEquals(1, body.size());
        assertEquals("spiffe", body.get(0).get("kind"));
        Map<String, Object> keyMaterial = (Map<String, Object>) body.get(0).get("keyMaterial");
        assertEquals("jwks_url", keyMaterial.get("source"));
        assertEquals("https://spire.example.org/keys", keyMaterial.get("jwksUrl"));
    }

    @Test
    public void shouldStillExposeDeprecatedBundleSource() {
        stubDomain();
        doReturn(Flowable.just(trustDomain(TrustDomainKind.SPIFFE, TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://spire.example.org/keys")
                .build())))
                .when(trustDomainService).findByReference(ReferenceType.DOMAIN, DOMAIN_ID);

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<Map<String, Object>> body = response.readEntity(List.class);
        assertEquals("jwks_url", body.get(0).get("bundleSource"));
        assertEquals("https://spire.example.org/keys", body.get(0).get("jwksUrl"));
    }

    @Test
    public void shouldCreateWithPemKeyMaterial() {
        Domain domain = stubDomain();
        TrustDomainKeyMaterial pem = TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.PEM)
                .certificate(PEM_CERTIFICATE)
                .build();
        doReturn(Single.just(trustDomain(TrustDomainKind.SPIFFE, pem)))
                .when(trustDomainService).create(eq(domain), any(NewTrustDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "kind", "SPIFFE",
                        "keyMaterial", Map.of("source", "PEM", "certificate", PEM_CERTIFICATE))));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        ArgumentCaptor<NewTrustDomain> captor = ArgumentCaptor.forClass(NewTrustDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), any());
        assertEquals(TrustDomainKind.SPIFFE, captor.getValue().getKind());
        assertEquals(KeyMaterialSource.PEM, captor.getValue().getKeyMaterial().getSource());
        assertEquals(PEM_CERTIFICATE, captor.getValue().getKeyMaterial().getCertificate());
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
        doReturn(Single.just(trustDomain(TrustDomainKind.SPIFFE, TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWK_SET)
                .jwkSet(jwkSet)
                .build())))
                .when(trustDomainService).create(eq(domain), any(NewTrustDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "kind", "SPIFFE",
                        "keyMaterial", Map.of("source", "JWK_SET", "jwkSet", Map.of("keys", List.of(Map.of(
                                "kty", "RSA",
                                "kid", "key-1",
                                "e", "AQAB",
                                "n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86z")))))));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        ArgumentCaptor<NewTrustDomain> captor = ArgumentCaptor.forClass(NewTrustDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), any());
        JWKSet received = captor.getValue().getKeyMaterial().getJwkSet();
        assertNotNull(received);
        assertEquals(1, received.getKeys().size());
        assertEquals("key-1", received.getKeys().get(0).getKid());
        assertEquals("RSA", received.getKeys().get(0).getKty());
    }

    @Test
    public void shouldCreateWithTokenExchangeKind() {
        Domain domain = stubDomain();
        doReturn(Single.just(trustDomain(TrustDomainKind.TOKEN_EXCHANGE, TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://issuer.example.org/keys")
                .build())))
                .when(trustDomainService).create(eq(domain), any(NewTrustDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "kind", "TOKEN_EXCHANGE",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://issuer.example.org/keys"))));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        Map<String, Object> body = response.readEntity(Map.class);
        assertEquals("token_exchange", body.get("kind"));
        assertEquals("jwks_url", body.get("bundleSource"));
    }

    @Test
    public void shouldRejectInvalidKeyMaterial() {
        Domain domain = stubDomain();
        doReturn(Single.error(new InvalidTrustDomainException("keyMaterial.source is required")))
                .when(trustDomainService).create(eq(domain), any(NewTrustDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of("name", "example.org", "kind", "SPIFFE")));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldReturnNotFound_whenDomainIsUnknown() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://spire.example.org/keys"))));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(trustDomainService, never()).create(any(), any(), any());
    }

    @Test
    public void shouldStillAcceptDeprecatedBundleSourceInput() {
        Domain domain = stubDomain();
        doReturn(Single.just(trustDomain(TrustDomainKind.SPIFFE, TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://spire.example.org/keys")
                .build())))
                .when(trustDomainService).create(eq(domain), any(NewTrustDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "bundleSource", "JWKS_URL",
                        "jwksUrl", "https://spire.example.org/keys")));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        ArgumentCaptor<NewTrustDomain> captor = ArgumentCaptor.forClass(NewTrustDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), any());
        assertNull(captor.getValue().getKind());
        assertNull(captor.getValue().getKeyMaterial());
        assertEquals(SpiffeBundleSource.JWKS_URL, captor.getValue().getBundleSource());
        assertEquals("https://spire.example.org/keys", captor.getValue().getJwksUrl());
    }

    private static TrustDomainTokenExchangeSettings tokenExchangeSettings() {
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute("emails.value");
        criterion.setExpression("{#token['email']}");
        return TrustDomainTokenExchangeSettings.builder()
                .issuer("https://issuer.example.org")
                .scopeMappings(Map.of("read", "domain:read"))
                .userBindingEnabled(true)
                .userBindingCriteria(List.of(criterion))
                .build();
    }

    private static TrustDomain tokenExchangeTrustDomain() {
        TrustDomain td = trustDomain(TrustDomainKind.TOKEN_EXCHANGE, TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://issuer.example.org/keys")
                .build());
        td.setTokenExchange(tokenExchangeSettings());
        return td;
    }

    @Test
    public void shouldCreateTokenExchangeTrustedDomainWithScopeMappingsAndUserBinding() {
        Domain domain = stubDomain();
        doReturn(Single.just(tokenExchangeTrustDomain()))
                .when(trustDomainService).create(eq(domain), any(NewTrustDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "issuer.example.org",
                        "kind", "TOKEN_EXCHANGE",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://issuer.example.org/keys"),
                        "tokenExchange", Map.of(
                                "issuer", "https://issuer.example.org",
                                "scopeMappings", Map.of("read", "domain:read"),
                                "userBindingEnabled", true,
                                "userBindingCriteria", List.of(Map.of(
                                        "attribute", "emails.value",
                                        "expression", "{#token['email']}"))))));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        ArgumentCaptor<NewTrustDomain> captor = ArgumentCaptor.forClass(NewTrustDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), any());
        TrustDomainTokenExchangeSettings received = captor.getValue().getTokenExchange();
        assertNotNull(received);
        assertEquals("https://issuer.example.org", received.getIssuer());
        assertEquals(Map.of("read", "domain:read"), received.getScopeMappings());
        assertTrue(received.isUserBindingEnabled());
        assertEquals(1, received.getUserBindingCriteria().size());
        assertEquals("emails.value", received.getUserBindingCriteria().get(0).getAttribute());
        assertEquals("{#token['email']}", received.getUserBindingCriteria().get(0).getExpression());
    }

    @Test
    public void shouldExposeTokenExchangeSettingsInList() {
        stubDomain();
        doReturn(Flowable.just(tokenExchangeTrustDomain()))
                .when(trustDomainService).findByReference(ReferenceType.DOMAIN, DOMAIN_ID);

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<Map<String, Object>> body = response.readEntity(List.class);
        assertEquals("token_exchange", body.get(0).get("kind"));
        Map<String, Object> tokenExchange = (Map<String, Object>) body.get(0).get("tokenExchange");
        assertEquals("https://issuer.example.org", tokenExchange.get("issuer"));
        assertEquals(Map.of("read", "domain:read"), tokenExchange.get("scopeMappings"));
        assertEquals(Boolean.TRUE, tokenExchange.get("userBindingEnabled"));
    }

    @Test
    public void shouldRejectListWithoutTrustDomainPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).findByReference(any(), any());
    }

    @Test
    public void shouldRejectCreateWithoutTrustDomainPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "issuer.example.org",
                        "kind", "TOKEN_EXCHANGE",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://issuer.example.org/keys"),
                        "tokenExchange", Map.of("issuer", "https://issuer.example.org"))));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).create(any(), any(), any());
    }

    @Test
    public void shouldRejectListWhenOnlyTheDeprecatedPermissionsAreHeld() {
        grantOnly(DOMAIN_ID, Permission.DOMAIN_SETTINGS, Permission.DOMAIN_OPENID);

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).findByReference(any(), any());
    }

    @Test
    public void shouldRejectCreateWhenOnlyTheDeprecatedPermissionsAreHeld() {
        grantOnly(DOMAIN_ID, Permission.DOMAIN_SETTINGS, Permission.DOMAIN_OPENID);

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "issuer.example.org",
                        "kind", "TOKEN_EXCHANGE",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://issuer.example.org/keys"),
                        "tokenExchange", Map.of("issuer", "https://issuer.example.org"))));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).create(any(), any(), any());
    }

    @Test
    public void shouldRejectSettingsOfTheOtherKind() {
        Domain domain = stubDomain();
        doReturn(Single.error(new InvalidTrustDomainException(
                "tokenExchange settings are only allowed on a trusted domain of kind TOKEN_EXCHANGE")))
                .when(trustDomainService).create(eq(domain), any(NewTrustDomain.class), any());

        final Response response = target("domains").path(DOMAIN_ID).path("trust-domains").request()
                .post(Entity.json(Map.of(
                        "name", "example.org",
                        "kind", "SPIFFE",
                        "keyMaterial", Map.of("source", "JWKS_URL", "jwksUrl", "https://spire.example.org/keys"),
                        "tokenExchange", Map.of("issuer", "https://issuer.example.org"))));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }
}
