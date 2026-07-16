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
package io.gravitee.am.management.handlers.management.api.resources.organizations;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.model.GraviteeLicense;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.node.api.license.License;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
public class LicenseResourceTest extends JerseySpringTest {

    private static final String ORGANIZATION_ID = Organization.DEFAULT;

    @Test
    public void shouldGetOrganizationLicense() {
        stubOrganization();
        stubLicense("ORGANIZATION");

        Response response = target("organizations").path(ORGANIZATION_ID).path("license").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        GraviteeLicense license = readEntity(response, GraviteeLicense.class);
        assertEquals("universe", license.getTier());
        assertEquals(Set.of("pack-1"), license.getPacks());
        assertEquals(Set.of("feature-1"), license.getFeatures());
        assertNotNull(license.getExpiresAt());
        assertEquals("ORGANIZATION", license.getScope());
    }

    @Test
    public void shouldGetPlatformLicenseAsFallback() {
        stubOrganization();
        stubLicense("PLATFORM");

        Response response = target("organizations").path(ORGANIZATION_ID).path("license").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        GraviteeLicense license = readEntity(response, GraviteeLicense.class);
        assertEquals("PLATFORM", license.getScope());
    }

    @Test
    public void shouldReturn403WhenNotAMemberOfTheOrganization() {
        Response response = target("organizations").path("another-org").path("license").request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldReturn404WhenOrganizationNotFound() {
        doReturn(Single.error(new OrganizationNotFoundException(ORGANIZATION_ID)))
                .when(organizationService).findById(ORGANIZATION_ID);

        Response response = target("organizations").path(ORGANIZATION_ID).path("license").request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    private void stubOrganization() {
        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        doReturn(Single.just(organization)).when(organizationService).findById(ORGANIZATION_ID);
    }

    private void stubLicense(String referenceType) {
        License license = mock(License.class);
        when(license.getTier()).thenReturn("universe");
        when(license.getPacks()).thenReturn(Set.of("pack-1"));
        when(license.getFeatures()).thenReturn(Set.of("feature-1"));
        when(license.getExpirationDate()).thenReturn(new Date());
        when(license.isExpired()).thenReturn(false);
        when(license.getReferenceType()).thenReturn(referenceType);
        doReturn(license).when(licenseManager).getOrganizationLicenseOrPlatform(ORGANIZATION_ID);
    }
}
