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
package io.gravitee.am.management.service.impl;

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
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class OrganizationLicenseManagerTest {

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
    private io.gravitee.node.api.license.License parsedLicense;

    @InjectMocks
    private OrganizationLicenseManager manager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldRegisterPersistedOrganizationLicensesOnStart() throws Exception {
        when(licenseService.findAll()).thenReturn(Flowable.just(
                license(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "license-1"),
                license(ReferenceType.PLATFORM, "platform", "license-2")));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORGANIZATION_ID, "license-1")).thenReturn(parsedLicense);

        manager.doStart();

        verify(eventManager).subscribeForEvents(manager, LicenseEvent.class);
        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, parsedLicense);
        verify(licenseFactory, never()).create(anyString(), Mockito.eq("platform"), anyString());
    }

    @Test
    public void shouldSkipUndecodableLicenseOnStart() throws Exception {
        when(licenseService.findAll()).thenReturn(Flowable.just(
                license(ReferenceType.ORGANIZATION, "bad-org", "corrupted"),
                license(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "license-1")));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), "bad-org", "corrupted"))
                .thenThrow(new IllegalArgumentException("Illegal base64 character"));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORGANIZATION_ID, "license-1")).thenReturn(parsedLicense);

        manager.doStart();

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, parsedLicense);
        verify(licenseManager, never()).registerOrganizationLicense(Mockito.eq("bad-org"), any());
    }

    @Test
    public void shouldRegisterLicenseOnDeployEvent() throws Exception {
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORGANIZATION_ID))
                .thenReturn(Maybe.just(license(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "license-1")));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORGANIZATION_ID, "license-1")).thenReturn(parsedLicense);

        manager.onEvent(new SimpleEvent<>(LicenseEvent.DEPLOY, payload(Action.CREATE)));

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, parsedLicense);
    }

    @Test
    public void shouldRegisterLicenseOnUpdateEvent() throws Exception {
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORGANIZATION_ID))
                .thenReturn(Maybe.just(license(ReferenceType.ORGANIZATION, ORGANIZATION_ID, "license-1")));
        when(licenseFactory.create(ReferenceType.ORGANIZATION.name(), ORGANIZATION_ID, "license-1")).thenReturn(parsedLicense);

        manager.onEvent(new SimpleEvent<>(LicenseEvent.UPDATE, payload(Action.UPDATE)));

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, parsedLicense);
    }

    @Test
    public void shouldDeregisterLicenseOnUndeployEvent() {
        manager.onEvent(new SimpleEvent<>(LicenseEvent.UNDEPLOY, payload(Action.DELETE)));

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, null);
        verifyNoInteractions(licenseService);
    }

    @Test
    public void shouldDeregisterLicenseWhenDeployedLicenseIsMissing() {
        when(licenseService.findByReference(ReferenceType.ORGANIZATION, ORGANIZATION_ID)).thenReturn(Maybe.empty());

        manager.onEvent(new SimpleEvent<>(LicenseEvent.DEPLOY, payload(Action.CREATE)));

        verify(licenseManager).registerOrganizationLicense(ORGANIZATION_ID, null);
    }

    @Test
    public void shouldIgnoreNonOrganizationEvent() {
        Payload payload = new Payload("domain#1", ReferenceType.DOMAIN, "domain#1", Action.CREATE);

        manager.onEvent(new SimpleEvent<>(LicenseEvent.DEPLOY, payload));

        verifyNoInteractions(licenseService, licenseManager);
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
