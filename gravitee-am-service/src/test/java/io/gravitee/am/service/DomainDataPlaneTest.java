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
package io.gravitee.am.service;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.login.WebAuthnSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DomainDataPlaneTest {
    private static final String ENVIRONMENT_ID = "env#1";
    private static final DataPlaneDescription DESCRIPTION = new DataPlaneDescription("id", "name", "jdbc", "base", "http://gravitee.io");

    private final EntryPointManager entryPointManager = mock();

    @BeforeEach
    public void init() {
        when(entryPointManager.resolveForRequest(any(), any())).thenCallRealMethod();
    }

    @Test
    public void if_webauthn_settings_is_empty_should_return_gateway_url_from_dataplane() {
        DomainDataPlane domainDataPlane = standalone(new Domain());

        assertEquals("http://gravitee.io", domainDataPlane.getWebAuthnOrigin(null));
    }

    @Test
    public void should_return_webauthn_origin_if_its_present() {
        DomainDataPlane domainDataPlane = standalone(withConfiguredOrigin(new Domain(), "http://gravitee2.io"));

        assertEquals("http://gravitee2.io", domainDataPlane.getWebAuthnOrigin(null));
    }

    @Test
    public void should_ignore_the_entrypoints_outside_managed_cloud() {
        DomainDataPlane domainDataPlane = standalone(cloudDomain());

        assertEquals("http://gravitee.io", domainDataPlane.getWebAuthnOrigin("https://custom.acme.com"));
        verifyNoInteractions(entryPointManager);
    }

    @Test
    public void should_return_the_primary_entrypoint_in_managed_cloud() {
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com")));

        assertEquals("https://auth.acme.com", managedCloud(cloudDomain()).getWebAuthnOrigin(null));
    }

    @Test
    public void should_prefer_the_entrypoint_the_request_came_in_on() {
        when(entryPointManager.findAllByEnvironmentId(ENVIRONMENT_ID))
                .thenReturn(List.of(entrypoint("https://auth.acme.com"), entrypoint("https://custom.acme.com")));

        assertEquals("https://custom.acme.com", managedCloud(cloudDomain()).getWebAuthnOrigin("https://custom.acme.com"));
    }

    @Test
    public void should_fall_back_to_the_primary_entrypoint_when_the_request_origin_is_unknown() {
        when(entryPointManager.findAllByEnvironmentId(ENVIRONMENT_ID)).thenReturn(List.of(entrypoint("https://auth.acme.com")));
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com")));

        assertEquals("https://auth.acme.com", managedCloud(cloudDomain()).getWebAuthnOrigin("https://evil.example"));
    }

    @Test
    public void the_entrypoint_beats_a_configured_origin_in_managed_cloud() {
        Domain domain = withConfiguredOrigin(cloudDomain(), "https://stale.acme.com");
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com")));

        assertEquals("https://auth.acme.com", managedCloud(domain).getWebAuthnOrigin(null));
    }

    @Test
    public void should_cut_the_entrypoint_back_to_an_origin() {
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com:8443/some/path/")));

        assertEquals("https://auth.acme.com:8443", managedCloud(cloudDomain()).getWebAuthnOrigin(null));
    }

    @Test
    public void should_serialize_the_entrypoint_origin_the_way_the_browser_does() {
        // Vert.x compares this against clientDataJSON.origin by string equality, and the browser sends
        // neither the scheme's default port nor the casing the entrypoint was stored with.
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://Auth.Acme.com:443/")));

        assertEquals("https://auth.acme.com", managedCloud(cloudDomain()).getWebAuthnOrigin(null));
    }

    @Test
    public void should_fall_back_to_the_gateway_url_when_the_environment_has_no_entrypoint() {
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.empty());

        assertEquals("http://gravitee.io", managedCloud(cloudDomain()).getWebAuthnOrigin(null));
    }

    @Test
    public void should_fall_back_to_the_gateway_url_when_the_domain_has_no_environment() {
        assertEquals("http://gravitee.io", managedCloud(new Domain()).getWebAuthnOrigin(null));
        verifyNoInteractions(entryPointManager);
    }

    @Test
    public void the_entrypoint_origin_is_empty_whenever_the_origin_did_not_come_from_one() {
        // Callers deriving a relying party id key off this: a fallback origin must not produce one, or a
        // domain that configured its own relying party id would have it replaced by the origin's host.
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.empty());

        assertTrue(standalone(cloudDomain()).getWebAuthnEntrypointOrigin(null).isEmpty());
        assertTrue(managedCloud(new Domain()).getWebAuthnEntrypointOrigin(null).isEmpty());
        assertTrue(managedCloud(cloudDomain()).getWebAuthnEntrypointOrigin(null).isEmpty());
    }

    @Test
    public void the_entrypoint_origin_is_present_when_the_environment_resolves_one() {
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com")));

        assertEquals(Optional.of("https://auth.acme.com"), managedCloud(cloudDomain()).getWebAuthnEntrypointOrigin(null));
    }

    private DomainDataPlane standalone(Domain domain) {
        return new DomainDataPlane(domain, DESCRIPTION, entryPointManager, false);
    }

    private DomainDataPlane managedCloud(Domain domain) {
        return new DomainDataPlane(domain, DESCRIPTION, entryPointManager, true);
    }

    private static Domain cloudDomain() {
        Domain domain = new Domain();
        domain.setReferenceId(ENVIRONMENT_ID);
        return domain;
    }

    private static Domain withConfiguredOrigin(Domain domain, String origin) {
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setOrigin(origin);
        domain.setWebAuthnSettings(webAuthnSettings);
        return domain;
    }

    private static Entrypoint entrypoint(String url) {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setUrl(url);
        return entrypoint;
    }
}
