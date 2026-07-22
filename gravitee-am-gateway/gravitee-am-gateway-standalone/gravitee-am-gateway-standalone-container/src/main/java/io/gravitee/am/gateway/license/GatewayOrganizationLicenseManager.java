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
import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.LicenseService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseFactory;
import io.gravitee.node.api.license.LicenseManager;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Feeds the node {@link LicenseManager} with the organization licenses persisted by the cockpit/cloud
 * command flow, so runtime license gating can resolve an organization's license on the gateway.
 * <p>
 * In managed cloud mode, a license change or expiry triggers a redeployment of the affected
 * organization's domains so their managers re-evaluate plugin gating against the new license.
 *
 * @author GraviteeSource Team
 */
public class GatewayOrganizationLicenseManager extends AbstractService<GatewayOrganizationLicenseManager> implements EventListener<LicenseEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(GatewayOrganizationLicenseManager.class);

    private final LicenseService licenseService;
    private final LicenseFactory licenseFactory;
    private final LicenseManager licenseManager;
    private final EventManager eventManager;
    private final SecurityDomainManager securityDomainManager;
    private final EnvironmentService environmentService;
    private final boolean managedCloudEnabled;

    private ExecutorService redeployExecutor;
    private Scheduler redeployScheduler;

    public GatewayOrganizationLicenseManager(LicenseService licenseService,
                                             LicenseFactory licenseFactory,
                                             LicenseManager licenseManager,
                                             EventManager eventManager,
                                             SecurityDomainManager securityDomainManager,
                                             EnvironmentService environmentService,
                                             org.springframework.core.env.Environment environment) {
        this.licenseService = licenseService;
        this.licenseFactory = licenseFactory;
        this.licenseManager = licenseManager;
        this.eventManager = eventManager;
        this.securityDomainManager = securityDomainManager;
        this.environmentService = environmentService;
        this.managedCloudEnabled = CloudProperties.isManagedCloudEnabled(environment);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!managedCloudEnabled) {
            logger.debug("Not a managed cloud deployment, skipping organization license management");
            return;
        }

        logger.info("Register event listener for license events for the gateway");
        eventManager.subscribeForEvents(this, LicenseEvent.class);

        // set up a single thread for redeploying domains
        final ClassLoader deploymentClassLoader = SecurityDomainManager.class.getClassLoader();
        redeployExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gio.license-redeployer");
            t.setDaemon(true);
            t.setContextClassLoader(deploymentClassLoader);
            return t;
        });
        redeployScheduler = Schedulers.from(redeployExecutor);

        // block here since licenses must be registered before the Reactor starts and the first domain deploys
        try {
            licenseService.findAll()
                    .filter(license -> license.getReferenceType() == ReferenceType.ORGANIZATION)
                    .doOnNext(license -> logger.info("Initializing license for organization={}", license.getReferenceId()))
                    .blockingForEach(license -> register(license.getReferenceId(), license.getLicense()));
        } catch (Exception e) {
            // Degrade to OSS: domains deploy without EE plugins and the next license event heals it.
            logger.error("An error occurred while loading organization licenses", e);
        }

        licenseManager.onLicenseExpires(license -> {
            if (License.REFERENCE_TYPE_ORGANIZATION.equals(license.getReferenceType())) {
                logger.info("License of organization={} has expired", license.getReferenceId());
                redeployOrganizationDomains(license.getReferenceId());
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        eventManager.unsubscribeForEvents(this, LicenseEvent.class);
        if (redeployExecutor != null) {
            redeployExecutor.shutdown();
        }
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
                .subscribe(license -> {
                            register(organizationId, license.getLicense());
                            redeployOrganizationDomains(organizationId);
                        },
                        ex -> logger.error("An error occurred while loading license for organization={}", organizationId, ex),
                        () -> undeploy(organizationId));
    }

    private void undeploy(String organizationId) {
        licenseManager.registerOrganizationLicense(organizationId, null);
        redeployOrganizationDomains(organizationId);
    }

    private void register(String organizationId, String rawLicense) {
        try {
            // an invalid organization license degrades to the OSS license (DefaultLicenseFactory semantics);
            // the factory only throws on undecodable input (e.g. a corrupted persisted row)
            licenseManager.registerOrganizationLicense(organizationId,
                    licenseFactory.create(ReferenceType.ORGANIZATION.name(), organizationId, rawLicense));
        } catch (Exception e) {
            logger.warn("License cannot be registered for organization={}", organizationId, e);
        }
    }

    private void redeployOrganizationDomains(String organizationId) {
        logger.info("Redeploying domains of organization={} following a license change", organizationId);
        Flowable.fromIterable(securityDomainManager.domains())
                .flatMapMaybe(domain -> environmentService.findById(domain.getReferenceId())
                        .map(Environment::getOrganizationId)
                        .filter(organizationId::equals)
                        .map(orgId -> domain)
                        .onErrorResumeNext(ex -> {
                            logger.warn("Cannot resolve the organization of domain={}, skipping its redeployment", domain.getId(), ex);
                            return Maybe.empty();
                        }))
                .observeOn(redeployScheduler)
                // A concurrent sync cycle may update the same domain; worst case is a redundant rebuild.
                .concatMapCompletable(domain -> securityDomainManager.updateReactive(domain)
                        .doOnError(ex -> logger.error("Unable to redeploy domain={} following a license change", domain.getId(), ex))
                        .onErrorComplete())
                .subscribe(
                        () -> logger.debug("Domains of organization={} redeployed", organizationId),
                        ex -> logger.error("An error occurred while redeploying domains of organization={}", organizationId, ex));
    }
}
