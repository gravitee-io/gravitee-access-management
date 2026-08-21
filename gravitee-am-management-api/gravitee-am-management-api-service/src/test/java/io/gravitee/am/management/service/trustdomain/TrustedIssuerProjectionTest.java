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
package io.gravitee.am.management.service.trustdomain;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.model.oidc.TrustDomainTokenExchangeSettings;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustedIssuerProjectionTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String ISSUER = "https://issuer.example.com";

    @Mock
    private TrustDomainService trustDomainService;

    @Mock
    private User principal;

    private TrustedIssuerProjection projection;

    @BeforeEach
    void setUp() {
        projection = new TrustedIssuerProjection(trustDomainService);
    }

    @Test
    void shouldAssembleTheInlineListFromTokenExchangeTrustedDomains() {
        Domain domain = domain();
        stubExisting(tokenExchange("td-1", "https-issuer.example.com", ISSUER), spiffe("td-2", "am.local"));

        Domain projected = projection.project(domain).blockingGet();

        List<TrustedIssuer> trustedIssuers = projected.getTokenExchangeSettings().getTrustedIssuers();
        assertEquals(1, trustedIssuers.size());
        assertEquals(ISSUER, trustedIssuers.get(0).getIssuer());
        assertEquals(KeyResolutionMethod.JWKS_URL, trustedIssuers.get(0).getKeyResolutionMethod());
        assertEquals(ISSUER + "/keys", trustedIssuers.get(0).getJwksUri());
        assertEquals(Map.of("ext:read", "read"), trustedIssuers.get(0).getScopeMappings());
        assertTrue(trustedIssuers.get(0).isUserBindingEnabled());
        assertEquals("email", trustedIssuers.get(0).getUserBindingCriteria().get(0).getAttribute());
    }

    @Test
    void shouldReportNoInlineListWhenNothingIsTrusted() {
        Domain domain = domain();
        domain.getTokenExchangeSettings().setTrustedIssuers(List.of(new TrustedIssuer()));
        stubExisting(spiffe("td-2", "am.local"));

        Domain projected = projection.project(domain).blockingGet();

        assertNull(projected.getTokenExchangeSettings().getTrustedIssuers());
    }

    @Test
    void shouldCreateATrustedDomainForAWrittenIssuer() {
        Domain domain = domain();
        stubExisting();
        when(trustDomainService.create(eq(domain), any(NewTrustDomain.class), any()))
                .thenReturn(Single.just(new TrustDomain()));

        projection.apply(domain, List.of(jwksIssuer(ISSUER)), principal).blockingAwait();

        ArgumentCaptor<NewTrustDomain> captor = ArgumentCaptor.forClass(NewTrustDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), eq(principal));
        assertEquals(TrustDomainKind.TOKEN_EXCHANGE, captor.getValue().getKind());
        assertEquals("https-issuer.example.com", captor.getValue().getName());
        assertEquals(ISSUER, captor.getValue().getTokenExchange().getIssuer());
        assertEquals(KeyMaterialSource.JWKS_URL, captor.getValue().getKeyMaterial().getSource());
        assertEquals(ISSUER + "/keys", captor.getValue().getKeyMaterial().getJwksUrl());
    }

    @Test
    void shouldAmendTheTrustedDomainAlreadyVouchingForAWrittenIssuer() {
        Domain domain = domain();
        stubExisting(tokenExchange("td-1", "https-issuer.example.com", ISSUER));
        when(trustDomainService.update(eq(domain), anyString(), any(UpdateTrustDomain.class), any()))
                .thenReturn(Single.just(new TrustDomain()));

        TrustedIssuer written = jwksIssuer(ISSUER);
        written.setScopeMappings(Map.of("ext:write", "write"));
        projection.apply(domain, List.of(written), principal).blockingAwait();

        ArgumentCaptor<UpdateTrustDomain> captor = ArgumentCaptor.forClass(UpdateTrustDomain.class);
        verify(trustDomainService).update(eq(domain), eq("td-1"), captor.capture(), eq(principal));
        assertEquals(Map.of("ext:write", "write"), captor.getValue().getTokenExchange().getScopeMappings());
        verify(trustDomainService, never()).create(any(), any(), any());
    }

    @Test
    void shouldDeleteTheTrustedDomainOfAnIssuerOmittedFromTheWrittenList() {
        Domain domain = domain();
        stubExisting(tokenExchange("td-1", "https-issuer.example.com", ISSUER),
                tokenExchange("td-2", "https-other.example.com", "https://other.example.com"));
        when(trustDomainService.update(eq(domain), anyString(), any(UpdateTrustDomain.class), any()))
                .thenReturn(Single.just(new TrustDomain()));
        when(trustDomainService.delete(eq(domain), anyString(), any())).thenReturn(Completable.complete());

        projection.apply(domain, List.of(jwksIssuer(ISSUER)), principal).blockingAwait();

        verify(trustDomainService).delete(domain, "td-2", principal);
        verify(trustDomainService, never()).delete(domain, "td-1", principal);
    }

    @Test
    void shouldDisambiguateAWrittenIssuerWhoseDerivedNameIsAlreadyHeld() {
        Domain domain = domain();
        stubExisting(tokenExchange("td-1", "https-issuer.example.com", ISSUER));
        when(trustDomainService.update(eq(domain), anyString(), any(UpdateTrustDomain.class), any()))
                .thenReturn(Single.just(new TrustDomain()));
        when(trustDomainService.create(eq(domain), any(NewTrustDomain.class), any()))
                .thenReturn(Single.just(new TrustDomain()));

        projection.apply(domain, List.of(jwksIssuer(ISSUER), jwksIssuer(ISSUER + "/")), principal).blockingAwait();

        ArgumentCaptor<NewTrustDomain> captor = ArgumentCaptor.forClass(NewTrustDomain.class);
        verify(trustDomainService).create(eq(domain), captor.capture(), eq(principal));
        assertNotEquals("https-issuer.example.com", captor.getValue().getName());
        assertTrue(captor.getValue().getName().startsWith("https-issuer.example.com-"));
    }

    @Test
    void shouldDeleteEveryTrustedDomainWhenTheWrittenListIsEmpty() {
        Domain domain = domain();
        stubExisting(tokenExchange("td-1", "https-issuer.example.com", ISSUER));
        when(trustDomainService.delete(eq(domain), anyString(), any())).thenReturn(Completable.complete());

        projection.apply(domain, List.of(), principal).blockingAwait();

        verify(trustDomainService).delete(domain, "td-1", principal);
    }

    @Test
    void shouldLeaveTrustedDomainsAloneWhenTheDeprecatedFieldWasNotWritten() {
        Domain domain = domain();

        projection.apply(domain, null, principal).blockingAwait();

        verify(trustDomainService, never()).findByReference(any(), any());
        verify(trustDomainService, never()).create(any(), any(), any());
        verify(trustDomainService, never()).delete(any(), any(), any());
    }

    private void stubExisting(TrustDomain... trustDomains) {
        when(trustDomainService.findByReference(ReferenceType.DOMAIN, DOMAIN_ID))
                .thenReturn(Flowable.fromArray(trustDomains));
    }

    private static Domain domain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setTokenExchangeSettings(new TokenExchangeSettings());
        return domain;
    }

    private static TrustedIssuer jwksIssuer(String issuer) {
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(issuer);
        trustedIssuer.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        trustedIssuer.setJwksUri(issuer + "/keys");
        return trustedIssuer;
    }

    private static TrustDomain tokenExchange(String id, String name, String issuer) {
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute("email");
        criterion.setExpression("{#token.email}");
        return TrustDomain.builder()
                .id(id)
                .kind(TrustDomainKind.TOKEN_EXCHANGE)
                .name(name)
                .keyMaterial(TrustDomainKeyMaterial.builder()
                        .source(KeyMaterialSource.JWKS_URL)
                        .jwksUrl(issuer + "/keys")
                        .build())
                .tokenExchange(TrustDomainTokenExchangeSettings.builder()
                        .issuer(issuer)
                        .scopeMappings(Map.of("ext:read", "read"))
                        .userBindingEnabled(true)
                        .userBindingCriteria(List.of(criterion))
                        .build())
                .build();
    }

    private static TrustDomain spiffe(String id, String name) {
        return TrustDomain.builder().id(id).kind(TrustDomainKind.SPIFFE).name(name).build();
    }
}
