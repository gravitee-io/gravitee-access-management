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
import io.gravitee.am.service.model.GraviteeLicense;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.node.api.license.License;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@TestPropertySource(properties = "license.expire-notification.enabled=false")
public class LicenseResourceExpireNotificationDisabledTest extends JerseySpringTest {

    private static final String ORGANIZATION_ID = "orga#1";

    @Test
    public void shouldNotExposeExpiresAtWhenExpireNotificationDisabled() {
        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        doReturn(Single.just(organization)).when(organizationService).findById(ORGANIZATION_ID);

        License license = mock(License.class);
        when(license.getTier()).thenReturn("universe");
        when(license.getExpirationDate()).thenReturn(new Date());
        when(license.getReferenceType()).thenReturn("ORGANIZATION");
        doReturn(license).when(licenseManager).getOrganizationLicenseOrPlatform(ORGANIZATION_ID);

        Response response = target("organizations").path(ORGANIZATION_ID).path("license").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        GraviteeLicense graviteeLicense = readEntity(response, GraviteeLicense.class);
        assertEquals("universe", graviteeLicense.getTier());
        assertNull(graviteeLicense.getExpiresAt());
    }
}
