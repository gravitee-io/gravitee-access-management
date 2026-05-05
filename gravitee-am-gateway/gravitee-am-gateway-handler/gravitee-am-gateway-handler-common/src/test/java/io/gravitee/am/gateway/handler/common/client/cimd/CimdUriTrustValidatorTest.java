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
package io.gravitee.am.gateway.handler.common.client.cimd;

import io.gravitee.am.common.web.PrivateOrReservedHostException;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.web.HostSsrfGuard;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Completable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CimdUriTrustValidatorTest {

    @Mock
    private HostSsrfGuard hostSsrfGuard;

    private CimdUriTrustValidator validator;

    @Before
    public void setUp() {
        validator = new CimdUriTrustValidator(hostSsrfGuard);
        when(hostSsrfGuard.assertNotPrivateHost(anyString())).thenReturn(Completable.complete());
    }

    @Test
    public void parseHttpUrlBuildsUriForHttps() throws Exception {
        URI uri = validator.parseHttpUrl("https://registry.example.com/app", "client_id");
        assertEquals("registry.example.com", uri.getHost());
    }

    @Test
    public void parseHttpUrlRejectsMalformedValue() {
        assertThrows(InvalidClientMetadataException.class, () -> validator.parseHttpUrl("not-a-url", "logo_uri"));
    }

    @Test
    public void validateTrustAcceptsHttpsByDefault() throws Exception {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        URI uri = UriBuilder.fromHttpUrl("https://registry.example.com/app").build();
        validator.validateTrust(uri, settings, "client_id");
    }

    @Test
    public void validateTrustRejectsHttpWhenUnsecuredDisabled() throws Exception {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        settings.setAllowUnsecuredHttpUri(false);
        URI uri = UriBuilder.fromHttpUrl("http://registry.example.com/app").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "logo_uri"));
    }

    @Test
    public void validateTrustAcceptsHttpWhenUnsecuredEnabled() throws Exception {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        settings.setAllowUnsecuredHttpUri(true);
        URI uri = UriBuilder.fromHttpUrl("http://registry.example.com/app").build();
        validator.validateTrust(uri, settings, "client_id");
    }

    @Test
    public void validateTrustRejectsHostOutsideAllowedDomains() throws Exception {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        settings.setAllowedDomains(List.of("*.vendor.example"));
        URI uri = UriBuilder.fromHttpUrl("https://evil.example/logo.png").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "logo_uri"));
    }

    @Test
    public void validateTrustAllowsHostInsideAllowedDomains() throws Exception {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        settings.setAllowedDomains(List.of("localhost"));
        URI uri = UriBuilder.fromHttpUrl("https://localhost/metadata").build();
        validator.validateTrust(uri, settings, "client_id");
    }

    @Test
    public void validateTrustRejectsLoopbackIpv4LiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://127.0.0.1/metadata").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "client_id"));
    }

    @Test
    public void validateTrustAllowsLoopbackIpv4WhenPrivateEnabled() throws Exception {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        URI uri = UriBuilder.fromHttpUrl("https://127.0.0.1/metadata").build();
        validator.validateTrust(uri, settings, "client_id");
    }

    @Test
    public void validateTrustRejectsPrivateClassAIpLiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://10.0.0.1/metadata").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "client_id"));
    }

    @Test
    public void validateTrustRejectsPrivateClassBIpLiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://172.16.0.1/metadata").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "client_id"));
    }

    @Test
    public void validateTrustRejectsPrivateClassCIpLiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://192.168.1.1/metadata").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "client_id"));
    }

    @Test
    public void validateTrustRejectsLinkLocalIpv4LiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://169.254.169.254/metadata").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "client_id"));
    }

    @Test
    public void validateTrustRejectsLoopbackIpv6LiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://[::1]/metadata").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "client_id"));
    }

    @Test
    public void validateTrustRejectsLinkLocalIpv6LiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://[fe80::1]/metadata").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "client_id"));
    }

    @Test
    public void validateTrustAllowsPublicIpv4LiteralWhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://8.8.8.8/metadata").build();
        validator.validateTrust(uri, settings, "client_id");
    }

    @Test
    public void validateTrustAllowsPrivateLiteralsWhenPrivateEnabled() throws Exception {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        validator.validateTrust(UriBuilder.fromHttpUrl("https://10.0.0.1/metadata").build(), settings, "client_id");
    }

    @Test
    public void validateTrustRejectsLogoAuthorityPrivateIpv4WhenPrivateDisabled() throws Exception {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        URI uri = UriBuilder.fromHttpUrl("https://192.168.0.1/logo.png").build();
        assertThrows(InvalidClientMetadataException.class, () -> validator.validateTrust(uri, settings, "logo_uri"));
    }

    @Test
    public void validateResolvableHostSkipsDnsWhenPrivateIpsAllowed() {
        CIMDSettings settings = settingsAllowPrivateLiterals();
        validator.validateResolvableHost("host.example", "client_id", settings).test().assertComplete();
        verifyNoInteractions(hostSsrfGuard);
    }

    @Test
    public void validateResolvableHostCompletesWhenGuardAllows() {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        validator.validateResolvableHost("external.example.com", "client_id", settings).test().assertComplete();
        verify(hostSsrfGuard).assertNotPrivateHost(eq("external.example.com"));
    }

    @Test
    public void validateResolvableHostMapsPrivateDnsFailureToInvalidMetadata() {
        when(hostSsrfGuard.assertNotPrivateHost(eq("vendor.example")))
                .thenReturn(Completable.error(new PrivateOrReservedHostException("loopback")));
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(false);
        validator.validateResolvableHost("vendor.example", "logo_uri", settings).test().assertFailure(InvalidClientMetadataException.class);
    }

    private static CIMDSettings baseSettings() {
        CIMDSettings settings = new CIMDSettings();
        settings.setAllowUnsecuredHttpUri(false);
        return settings;
    }

    private static CIMDSettings settingsAllowPrivateLiterals() {
        CIMDSettings settings = baseSettings();
        settings.setAllowPrivateIpAddress(true);
        return settings;
    }
}
