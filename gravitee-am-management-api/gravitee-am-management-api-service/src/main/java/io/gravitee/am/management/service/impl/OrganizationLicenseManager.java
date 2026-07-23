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

import io.gravitee.am.common.event.LicenseEvent;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.LicenseService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.license.LicenseFactory;
import io.gravitee.node.api.license.LicenseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.CustomLog;

/**
 * Feeds the node {@link LicenseManager} with the organization licenses persisted by the cockpit/cloud command flow,
 * so in-process consumers (REST resources, feature gating) can resolve an organization's license.
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class OrganizationLicenseManager extends AbstractService<OrganizationLicenseManager> implements EventListener<LicenseEvent, Payload> {


    @Autowired
    private LicenseService licenseService;

    @Autowired
    private LicenseFactory licenseFactory;

    @Autowired
    private LicenseManager licenseManager;

    @Autowired
    private EventManager eventManager;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        log.info("Register event listener for license events for the management API");
        eventManager.subscribeForEvents(this, LicenseEvent.class);

        licenseService.findAll()
                .filter(license -> license.getReferenceType() == ReferenceType.ORGANIZATION)
                .doOnNext(license -> log.info("Initializing license for organization={}", license.getReferenceId()))
                .subscribe(license -> register(license.getReferenceId(), license.getLicense()),
                        ex -> log.error("An error occurred while loading organization licenses", ex));
    }

    @Override
    public void onEvent(Event<LicenseEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.ORGANIZATION) {
            switch (event.type()) {
                case DEPLOY, UPDATE -> deploy(event.content().getReferenceId());
                case UNDEPLOY -> undeploy(event.content().getReferenceId());
            }
        }
    }

    private void deploy(String organizationId) {
        licenseService.findByReference(ReferenceType.ORGANIZATION, organizationId)
                .subscribe(license -> register(organizationId, license.getLicense()),
                        ex -> log.error("An error occurred while loading license for organization={}", organizationId, ex),
                        () -> undeploy(organizationId));
    }

    private void undeploy(String organizationId) {
        licenseManager.registerOrganizationLicense(organizationId, null);
    }

    private void register(String organizationId, String rawLicense) {
        try {
            // an invalid organization license degrades to the OSS license (DefaultLicenseFactory semantics);
            // the factory only throws on undecodable input (e.g. a corrupted persisted row)
            licenseManager.registerOrganizationLicense(organizationId,
                    licenseFactory.create(ReferenceType.ORGANIZATION.name(), organizationId, rawLicense));
        } catch (Exception e) {
            log.warn("License cannot be registered for organization={}", organizationId, e);
        }
    }
}
