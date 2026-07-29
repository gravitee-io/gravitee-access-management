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
package io.gravitee.am.gateway.handler.vertx;

import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.license.DomainPluginLicenseGate;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.authenticator.core.AuthenticatorPluginManager;
import io.gravitee.am.plugins.protocol.core.ProtocolPluginManager;
import io.gravitee.am.plugins.protocol.core.ProtocolProviderConfiguration;
import io.gravitee.am.service.PluginLicenseGate;
import io.vertx.rxjava3.ext.web.Route;
import io.vertx.rxjava3.ext.web.Router;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class VertxSecurityDomainHandlerTest {

    @Mock
    private Domain domain;

    @Mock
    private ProtocolPluginManager protocolPluginManager;

    @Mock
    private AuthenticatorPluginManager authenticatorPluginManager;

    @Mock
    private DomainPluginLicenseGate domainPluginLicenseGate;

    @Mock
    private DomainReadinessService domainReadinessService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Router router;

    @Mock
    private Environment environment;

    @Captor
    private ArgumentCaptor<Predicate<String>> authenticatorFilterCaptor;

    @InjectMocks
    private VertxSecurityDomainHandler handler;

    @Before
    public void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(handler, "applicationContext", applicationContext);
        lenient().when(domain.getName()).thenReturn("domain-name");

        Route route = mock(Route.class);
        lenient().when(router.route()).thenReturn(route);
        lenient().when(route.last()).thenReturn(route);
        lenient().when(route.handler(any())).thenReturn(route);

        lenient().when(applicationContext.getBean(RootProvider.class)).thenReturn(mock(RootProvider.class));
        lenient().when(domainPluginLicenseGate.check(any(), any(), any())).thenReturn(true);
        lenient().when(protocolPluginManager.create(any())).thenReturn(null);
        lenient().when(authenticatorPluginManager.createAll(any(), any())).thenReturn(List.of());
    }

    @Test
    public void shouldNotCreateUnlicensedProtocol() throws Exception {
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_PROTOCOL, "saml2-idp", "saml2-idp")).thenReturn(false);

        handler.doStart();

        ArgumentCaptor<ProtocolProviderConfiguration> configCaptor = ArgumentCaptor.forClass(ProtocolProviderConfiguration.class);
        verify(protocolPluginManager, org.mockito.Mockito.atLeastOnce()).create(configCaptor.capture());
        List<String> createdProtocols = configCaptor.getAllValues().stream().map(ProtocolProviderConfiguration::getType).toList();
        assertFalse(createdProtocols.contains("saml2-idp"));
        assertTrue(createdProtocols.contains("openid-connect"));
    }

    @Test
    public void shouldFilterAuthenticatorsThroughLicenseGate() throws Exception {
        handler.doStart();

        verify(authenticatorPluginManager).createAll(eq(applicationContext), authenticatorFilterCaptor.capture());

        Predicate<String> filter = authenticatorFilterCaptor.getValue();
        when(domainPluginLicenseGate.check(PluginLicenseGate.TYPE_AUTHENTICATOR, "ee-authenticator", "ee-authenticator")).thenReturn(false);
        assertFalse(filter.test("ee-authenticator"));
        assertTrue(filter.test("oss-authenticator"));

        // both are registered with their type so the readiness entry is not left type-less
        verify(domainReadinessService).initPluginSync(domain.getId(), "oss-authenticator", "AUTHENTICATOR");
        verify(domainReadinessService).initPluginSync(domain.getId(), "ee-authenticator", "AUTHENTICATOR");
        // a licensed authenticator must clear any stale "unlicensed" readiness; an unlicensed one must not
        verify(domainReadinessService).pluginLoaded(domain.getId(), "oss-authenticator");
        verify(domainReadinessService, never()).pluginLoaded(domain.getId(), "ee-authenticator");
    }

    @Test
    public void shouldRegisterAndClearReadinessForLicensedProtocol() throws Exception {
        when(protocolPluginManager.create(any())).thenReturn(mock(ProtocolProvider.class));

        handler.doStart();

        // every protocol is registered with its type, and a loaded one clears its readiness entry
        verify(domainReadinessService).initPluginSync(domain.getId(), "openid-connect", "PROTOCOL");
        verify(domainReadinessService).pluginLoaded(domain.getId(), "openid-connect");
    }

    @Test
    public void shouldDropReadinessForProtocolWithoutProvider() throws Exception {
        // create returns null (protocol not installed on this node): the seeded entry must be removed
        when(protocolPluginManager.create(any())).thenReturn(null);

        handler.doStart();

        verify(domainReadinessService).initPluginSync(domain.getId(), "openid-connect", "PROTOCOL");
        verify(domainReadinessService).pluginUnloaded(domain.getId(), "openid-connect");
        verify(domainReadinessService, never()).pluginLoaded(domain.getId(), "openid-connect");
    }

    @Test
    public void shouldRecordFailedProtocolAsFailed() throws Exception {
        ProtocolProvider provider = mock(ProtocolProvider.class);
        doThrow(new RuntimeException("boom")).when(provider).start();
        when(protocolPluginManager.create(any())).thenReturn(provider);

        handler.doStart();

        verify(domainReadinessService, atLeastOnce()).pluginFailed(eq(domain.getId()), anyString(), eq("boom"));
        verify(domainReadinessService, never()).pluginLoaded(eq(domain.getId()), anyString());
    }
}
