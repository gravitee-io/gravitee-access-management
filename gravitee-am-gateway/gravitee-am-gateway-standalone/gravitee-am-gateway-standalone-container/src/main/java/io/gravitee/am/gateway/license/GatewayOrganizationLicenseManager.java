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

import io.gravitee.am.common.env.CloudProperties;
import io.gravitee.am.common.event.LicenseEvent;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.LicenseService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseFactory;
import io.gravitee.node.api.license.LicenseManager;
import lombok.CustomLog;

/**
 * Feeds the node {@link LicenseManager} with the organization licenses persisted by the cockpit/cloud
 * command flow, so runtime license gating can resolve an organization's license on the gateway.
 * <p>
 * In managed cloud mode, feature gating is enforced when a domain redeploys: its plugin managers
 * consult the in-memory license through {@code io.gravitee.am.service.PluginLicenseGate}. This manager
 * only keeps that in-memory view current.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class GatewayOrganizationLicenseManager extends AbstractService<GatewayOrganizationLicenseManager> implements EventListener<LicenseEvent, Payload> {


    private final LicenseService licenseService;
    private final LicenseFactory licenseFactory;
    private final LicenseManager licenseManager;
    private final EventManager eventManager;
    private final boolean managedCloudEnabled;

    public GatewayOrganizationLicenseManager(LicenseService licenseService,
                                             LicenseFactory licenseFactory,
                                             LicenseManager licenseManager,
                                             EventManager eventManager,
                                             org.springframework.core.env.Environment environment) {
        this.licenseService = licenseService;
        this.licenseFactory = licenseFactory;
        this.licenseManager = licenseManager;
        this.eventManager = eventManager;
        this.managedCloudEnabled = CloudProperties.isManagedCloudEnabled(environment);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!managedCloudEnabled) {
            log.debug("Not a managed cloud deployment, skipping organization license management");
            return;
        }

        log.info("Register event listener for license events for the gateway");
        eventManager.subscribeForEvents(this, LicenseEvent.class);

        // block here since licenses must be registered before the Reactor starts and the first domain deploys
        try {
            licenseService.findAll()
                    .filter(license -> license.getReferenceType() == ReferenceType.ORGANIZATION)
                    .doOnNext(license -> log.info("Initializing license for organization={}", license.getReferenceId()))
                    .blockingForEach(license -> register(license.getReferenceId(), license.getLicense()));
        } catch (Exception e) {
            // Degrade to OSS: domains deploy without EE plugins and the next license event heals it.
            log.error("An error occurred while loading organization licenses", e);
        }

        // Log expiries for observability only. Enforcement is deferred to the next domain redeployment.
        licenseManager.onLicenseExpires(license -> {
            if (License.REFERENCE_TYPE_ORGANIZATION.equals(license.getReferenceType())) {
                log.info("License of organization={} has expired; restrictions will take effect on the next domain update or restart", license.getReferenceId());
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        eventManager.unsubscribeForEvents(this, LicenseEvent.class);
        super.doStop();
    }

    @Override
    public void onEvent(Event<LicenseEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.ORGANIZATION) {
            final String organizationId = event.content().getReferenceId();
            switch (event.type()) {
                case DEPLOY, UPDATE -> deploy(organizationId);
                case UNDEPLOY -> undeploy(organizationId);
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
