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
package io.gravitee.am.management.handlers.management.api.resources.platform;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.service.model.GraviteeLicense;
import io.gravitee.common.http.MediaType;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseManager;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LicenseResource extends AbstractResource {

    @Autowired
    private LicenseManager licenseManager;

    @Value("${license.expire-notification.enabled:true}")
    private boolean isLicenseExpirationNotifierEnabled = true;

    @GET
    @Operation(
            operationId = "getLicense",
            summary = "Get current node License")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public void get(@Suspended final AsyncResponse response) {
        final License platformLicense = licenseManager.getPlatformLicense();
        GraviteeLicense license = GraviteeLicense.builder()
                .tier(platformLicense.getTier())
                .packs(platformLicense.getPacks())
                .features(platformLicense.getFeatures())
                .expiresAt(isLicenseExpirationNotifierEnabled ? platformLicense.getExpirationDate() : null)
                .isExpired(platformLicense.isExpired())
                .build();
        response.resume(license);
    }

}
