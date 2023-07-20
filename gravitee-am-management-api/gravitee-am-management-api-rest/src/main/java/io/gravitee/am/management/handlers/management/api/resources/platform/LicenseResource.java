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
import io.gravitee.node.api.license.NodeLicenseService;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LicenseResource extends AbstractResource {

    @Inject
    private NodeLicenseService licenseService;

    @GET
    @ApiOperation(value = "Get current node License")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public void get(@Suspended final AsyncResponse response) {
        GraviteeLicense license = GraviteeLicense.builder()
                .tier(licenseService.getTier())
                .packs(licenseService.getPacks())
                .features(licenseService.getFeatures())
                .build();

        Single.just(license)
                .subscribe(response::resume, response::resume);
    }

}
