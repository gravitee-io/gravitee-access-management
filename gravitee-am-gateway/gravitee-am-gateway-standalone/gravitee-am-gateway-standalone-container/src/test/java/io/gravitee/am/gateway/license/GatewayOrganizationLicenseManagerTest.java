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
package io.gravitee.am.gateway.license;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.LicenseEvent;
import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.LicenseService;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import io.gravitee.node.api.license.LicenseFactory;
import io.gravitee.node.api.license.LicenseManager;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class GatewayOrganizationLicenseManagerTest {

    private static final String ORGANIZATION_ID = "orga#1";

    @Mock
    private LicenseService licenseService;

    @Mock
    private LicenseFactory licenseFactory;

    @Mock
    private LicenseManager licenseManager;

    @Mock
    private EventManager eventManager;

    @Mock
    private Environment environment;

    @Mock
    private io.gravitee.node.api.license.License parsedLicense;

    @Captor
    private ArgumentCaptor<Consumer<io.gravitee.node.api.license.License>> expirationListenerCaptor;

    private GatewayOrganizationLicenseManager manager;

    @Before
    public void setUp() {
        lenient().when(licenseService.findAll()).thenReturn(Flowable.empty());
    }

    @After
    public void tearDown() {
        if (manager != null) {
            try {
                manager.doStop();
            } catch (Exception e) {
                // already stopped by the test itself
            }
        }
    }

    private GatewayOrganizationLicenseManager manager(boolean managedCloud) {
        lenient().when(environment.getProperty("cloud.enabled", Boolean.class)).thenReturn(managedCloud);
        lenient().when(environment.getProperty("installation.type", "standalone")).thenReturn(managedCloud ? "managed" : "standalone");
        manager = new GatewayOrganizationLicenseManager(licenseService, licenseFactory, licenseManager,
                eventManager, environment);
        return manager;
    }

    @Test
    public void shouldRegisterPersistedOrganizationLicensesOnStartBeforeReturning() throws Exception {
        when(licenseService.findAll()).thenReturn(Flowable.just(
                license(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "license-1"),
                license(ReferenceType.PLATFORM, "platform", "license-2")));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORGANIZATION_ID, "license-1")).thenReturn(parsedLicense);

        manager(true).doStart();

        verify(eventManager).subscribeForEvents(manager, LicenseEvent.class);
        // blocking load: the license must be registered by the time doStart returns
        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, parsedLicense);
        verify(licenseFactory, never()).create(anyString(), org.mockito.ArgumentMatchers.eq("platform"), anyString());
    }

    @Test
    public void shouldSkipUndecodableLicenseOnStart() throws Exception {
        when(licenseService.findAll()).thenReturn(Flowable.just(
                license(ReferenceType.ORGANIZATION, "bad-org", "corrupted"),
                license(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "license-1")));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), "bad-org", "corrupted"))
                .thenThrow(new IllegalArgumentException("Illegal base64 character"));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORGANIZATION_ID, "license-1")).thenReturn(parsedLicense);

        manager(true).doStart();

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, parsedLicense);
        verify(licenseManager, never()).registerOrganizationLicense(org.mockito.ArgumentMatchers.eq("bad-org"), any());
    }

    @Test
    public void shouldTolerateInitialLoadFailure() throws Exception {
        when(licenseService.findAll()).thenReturn(Flowable.error(new RuntimeException("database unavailable")));

        manager(true).doStart();

        verify(eventManager).subscribeForEvents(manager, LicenseEvent.class);
        verify(licenseManager, never()).registerOrganizationLicense(anyString(), any());
    }

    @Test
    public void shouldUnsubscribeOnStop() throws Exception {
        manager(true).doStart();
        manager.doStop();

        verify(eventManager).unsubscribeForEvents(manager, LicenseEvent.class);
    }

    @Test
    public void shouldRegisterLicenseOnDeployEvent() throws Exception {
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORGANIZATION_ID))
                .thenReturn(Maybe.just(license(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "license-1")));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORGANIZATION_ID, "license-1")).thenReturn(parsedLicense);

        manager(true).doStart();
        manager.onEvent(new SimpleEvent<>(LicenseEvent.DEPLOY, payload(Action.CREATE)));

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, parsedLicense);
    }

    @Test
    public void shouldDeregisterLicenseOnUndeployEvent() throws Exception {
        manager(true).doStart();
        manager.onEvent(new SimpleEvent<>(LicenseEvent.UNDEPLOY, payload(Action.DELETE)));

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, null);
    }

    @Test
    public void shouldDeregisterLicenseWhenDeployedLicenseIsMissing() throws Exception {
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        manager(true).doStart();
        manager.onEvent(new SimpleEvent<>(LicenseEvent.DEPLOY, payload(Action.CREATE)));

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, null);
    }

    @Test
    public void shouldIgnoreNonOrganizationEvent() throws Exception {
        manager(true).doStart();
        manager.onEvent(new SimpleEvent<>(LicenseEvent.DEPLOY, new Payload("domain#1", ReferenceType.DOMAIN, "domain#1", Action.CREATE)));

        verify(licenseService, never()).findByReference(any(), anyString());
        verify(licenseManager, never()).registerOrganizationLicense(anyString(), any());
    }

    @Test
    public void shouldRegisterExpiryListenerForObservabilityOnly() throws Exception {
        manager(true).doStart();

        verify(licenseManager).onLicenseExpires(expirationListenerCaptor.capture());
        when(parsedLicense.getReferenceType()).thenReturn(io.gravitee.node.api.license.License.REFERENCE_TYPE_ORGANIZATION);
        when(parsedLicense.getReferenceId()).thenReturn(ORGANIZATION_ID);

        expirationListenerCaptor.getValue().accept(parsedLicense);

        verify(licenseService, never()).findByReference(any(), anyString());
        verify(licenseManager, never()).registerOrganizationLicense(anyString(), any());
    }

    @Test
    public void shouldDoNothingWhenNotManagedCloud() throws Exception {
        manager(false).doStart();

        verifyNoInteractions(eventManager, licenseService, licenseManager);
    }

    private static License license(ReferenceType referenceType, String referenceId, String rawLicense) {
        License license = new License();
        license.setReferenceType(referenceType);
        license.setReferenceId(referenceId);
        license.setLicense(rawLicense);
        return license;
    }

    private static Payload payload(Action action) {
        return new Payload(ORGANIZATION_ID, ReferenceType.ORGANIZATION, ORGANIZATION_ID, action);
    }
}
