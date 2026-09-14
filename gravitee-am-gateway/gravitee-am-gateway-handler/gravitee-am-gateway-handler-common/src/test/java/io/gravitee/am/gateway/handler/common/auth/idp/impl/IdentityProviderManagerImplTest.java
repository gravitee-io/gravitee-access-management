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
package io.gravitee.am.gateway.handler.common.auth.idp.impl;

import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.gateway.handler.common.auth.idp.AmbiguousTrustedIssuerException;
import io.gravitee.am.gateway.handler.common.auth.idp.TrustedIssuerProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.license.DomainPluginLicenseGate;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.trustedissuer.AssertionVerifier;
import io.gravitee.am.identityprovider.api.trustedissuer.OAuthTrustedIssuer;
import io.gravitee.am.identityprovider.api.trustedissuer.TrustedIssuerIdp;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(MockitoJUnitRunner.class)
public class IdentityProviderManagerImplTest {

    @InjectMocks
    private IdentityProviderManagerImpl identityProviderManager;

    @Mock
    private Domain domain;

    @Mock
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @Mock
    private CertificateManager certificateManager;

    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    @Mock
    private DomainReadinessService domainReadinessService;

    @Mock
    private DomainPluginLicenseGate domainPluginLicenseGate;

    @Test
    public void shouldSkipUnlicensedIdentityProviderWithoutFailingReadiness() {
        when(domain.getId()).thenReturn("domain-id");
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("ee-idp");
        identityProvider.setType("kerberos-am-idp");
        when(identityProviderRepository.findAll(ReferenceType.DOMAIN, "domain-id")).thenReturn(Flowable.just(identityProvider));
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_IDENTITY_PROVIDER, "kerberos-am-idp", "ee-idp")).thenReturn(false);

        identityProviderManager.afterPropertiesSet();

        verifyNoInteractions(identityProviderPluginManager);
        verify(domainReadinessService, never()).pluginFailed(any(), any(), any());
        // the provider is registered with its type before the gate check, so the unlicensed entry is not type-less
        verify(domainReadinessService).initPluginSync("domain-id", "ee-idp", "IDENTITY_PROVIDER");
    }

    @Test
    public void shouldHandleTopLevelFailure() {
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("domain-name");
        when(identityProviderRepository.findAll(ReferenceType.DOMAIN, "domain-id"))
                .thenReturn(Flowable.error(new RuntimeException("Database error")));

        identityProviderManager.afterPropertiesSet();

        verify(domainReadinessService).pluginInitFailed("domain-id", "IDENTITY_PROVIDER", "Database error");
    }

    @Test
    public void shouldKeepReadinessRecordWhenLoadedIdpBecomesUnlicensedOnUpdate() throws Exception {
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("domain-name");

        IdentityProvider initialIdp = new IdentityProvider();
        initialIdp.setId("idp-id");
        initialIdp.setType("kerberos-am-idp");
        initialIdp.setConfiguration("{}");
        initialIdp.setUpdatedAt(new Date(1000));

        // a newer version so needDeployment() forces a redeployment on the update event
        IdentityProvider updatedIdp = new IdentityProvider();
        updatedIdp.setId("idp-id");
        updatedIdp.setType("kerberos-am-idp");
        updatedIdp.setConfiguration("{}");
        updatedIdp.setUpdatedAt(new Date(2000));

        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);
        when(identityProviderRepository.findAll(ReferenceType.DOMAIN, "domain-id")).thenReturn(Flowable.just(initialIdp));
        when(identityProviderRepository.findById("idp-id")).thenReturn(Maybe.just(updatedIdp));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class))).thenReturn(authenticationProvider);
        when(identityProviderPluginManager.create(eq("kerberos-am-idp"), any(), any(IdentityProvider.class)))
                .thenReturn(Single.just(Optional.empty()));
        // licensed on the initial load, then unlicensed on the following update event
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_IDENTITY_PROVIDER, "kerberos-am-idp", "idp-id"))
                .thenReturn(true, false);

        // load the licensed provider
        identityProviderManager.afterPropertiesSet();

        // when - an update event arrives and the provider is now unlicensed
        Payload payload = new Payload("idp-id", ReferenceType.DOMAIN, "domain-id", io.gravitee.am.common.event.Action.UPDATE);
        Event<IdentityProviderEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(IdentityProviderEvent.UPDATE);
        when(event.content()).thenReturn(payload);
        identityProviderManager.onEvent(event);

        // then - the running provider is stopped and no longer served, but the readiness record is left
        // untouched so the unlicensed entry recorded by the gate survives (rather than being wiped by an unload)
        verify(authenticationProvider, times(1)).stop();
        verify(domainReadinessService, never()).pluginUnloaded("domain-id", "idp-id");
        assertNull(identityProviderManager.getIdentityProvider("idp-id"));
    }

    @Test
    public void shouldFindTrustedIssuerProviderByIssuer() throws Exception {
        IdentityProvider oidc = identityProvider("oidc-idp");
        AuthenticationProvider trusted = trustedIssuerProvider(Optional.of("https://issuer.example.com"));
        load(oidc);
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class))).thenReturn(trusted);

        identityProviderManager.afterPropertiesSet();

        TrustedIssuerProvider found = identityProviderManager.findByIssuer("https://issuer.example.com").blockingGet();
        assertSame(trusted, found.authenticationProvider());
        assertSame(oidc, found.identityProvider());
        assertEquals("https://issuer.example.com", found.trustedIssuer().issuer());
    }

    @Test
    public void shouldRefuseIssuerClaimedByTwoProviders() throws Exception {
        AuthenticationProvider first = trustedIssuerProvider(Optional.of("https://issuer.example.com"));
        AuthenticationProvider second = trustedIssuerProvider(Optional.of("https://issuer.example.com"));
        load(identityProvider("first-idp"), identityProvider("second-idp"));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class))).thenReturn(first, second);

        identityProviderManager.afterPropertiesSet();

        identityProviderManager.findByIssuer("https://issuer.example.com").test()
                .assertNoValues()
                .assertError(AmbiguousTrustedIssuerException.class)
                .assertError(error -> "invalid_grant".equals(((OAuth2Exception) error).getOAuth2ErrorCode()));
    }

    @Test
    public void shouldReturnEmptyWhenNoProviderClaimsIssuer() throws Exception {
        AuthenticationProvider trusted = trustedIssuerProvider(Optional.of("https://issuer.example.com"));
        load(identityProvider("oidc-idp"));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class))).thenReturn(trusted);

        identityProviderManager.afterPropertiesSet();

        identityProviderManager.findByIssuer("https://other.example.com").test()
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void shouldNotMatchProviderWhoseDiscoveryHasNotCompleted() throws Exception {
        AuthenticationProvider discovering = trustedIssuerProvider(Optional.empty());
        load(identityProvider("oidc-idp"));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class))).thenReturn(discovering);

        identityProviderManager.afterPropertiesSet();

        identityProviderManager.findByIssuer("https://issuer.example.com").test()
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void shouldSkipProvidersThatAreNotTrustedIssuers() throws Exception {
        AuthenticationProvider trusted = trustedIssuerProvider(Optional.of("https://issuer.example.com"));
        load(identityProvider("inline-idp"), identityProvider("oidc-idp"));
        when(identityProviderPluginManager.create(any(AuthenticationProviderConfiguration.class)))
                .thenReturn(mock(AuthenticationProvider.class), trusted);

        identityProviderManager.afterPropertiesSet();

        assertSame(trusted, identityProviderManager.findByIssuer("https://issuer.example.com").blockingGet().authenticationProvider());
    }

    private void load(IdentityProvider... identityProviders) throws Exception {
        when(domain.getId()).thenReturn("domain-id");
        when(identityProviderRepository.findAll(ReferenceType.DOMAIN, "domain-id")).thenReturn(Flowable.fromArray(identityProviders));
        when(identityProviderPluginManager.create(eq("oauth2-generic-am-idp"), any(), any(IdentityProvider.class)))
                .thenReturn(Single.just(Optional.empty()));
        for (IdentityProvider identityProvider : identityProviders) {
            when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_IDENTITY_PROVIDER, "oauth2-generic-am-idp", identityProvider.getId())).thenReturn(true);
        }
    }

    private static IdentityProvider identityProvider(String id) {
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId(id);
        identityProvider.setType("oauth2-generic-am-idp");
        identityProvider.setConfiguration("{}");
        return identityProvider;
    }

    private static AuthenticationProvider trustedIssuerProvider(Optional<String> issuer) {
        AuthenticationProvider provider = mock(AuthenticationProvider.class, withSettings().extraInterfaces(TrustedIssuerIdp.class));
        when(((TrustedIssuerIdp) provider).trustedIssuer())
                .thenReturn(issuer.map(value -> new OAuthTrustedIssuer(value, mock(AssertionVerifier.class))));
        return provider;
    }
}
