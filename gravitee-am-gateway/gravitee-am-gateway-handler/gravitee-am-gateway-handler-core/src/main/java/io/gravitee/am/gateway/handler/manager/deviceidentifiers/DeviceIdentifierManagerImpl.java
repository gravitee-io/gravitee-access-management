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
package io.gravitee.am.gateway.handler.manager.deviceidentifiers;

import io.gravitee.am.common.event.DeviceIdentifierEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.service.DeviceIdentifierService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierManagerImpl extends AbstractService implements DeviceIdentifierManager, InitializingBean, EventListener<DeviceIdentifierEvent, Payload> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceIdentifierManagerImpl.class);
    private final ConcurrentMap<String, DeviceIdentifierProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DeviceIdentifier> deviceIdentifiers = new ConcurrentHashMap<>();

    @Autowired
    private DeviceIdentifierService deviceIdentifierService;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private DeviceIdentifierPluginManager deviceIdentifierPluginManager;

    @Autowired
    private DomainReadinessService domainReadinessService;

    public ConcurrentMap<String, DeviceIdentifier> getDeviceIdentifiers() {
        return deviceIdentifiers;
    }
    public ConcurrentMap<String, DeviceIdentifierProvider> getDeviceIdentifiersProviders() {
        return providers;
    }

    @Override
    public void afterPropertiesSet() {
        LOGGER.info("Initializing device identifiers for domain {}", domain.getName());
        deviceIdentifierService.findByDomain(domain.getId())
                .subscribe(
                        remeberDevice -> {
                            updateDeviceIdentifier(remeberDevice);
                            LOGGER.info("Device identifier {} loaded for domain {}", remeberDevice.getName(), domain.getName());
                        },
                        error -> LOGGER.error("Unable to initialize device identifiers for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOGGER.info("Register event listener for device identifiers events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, DeviceIdentifierEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOGGER.info("Dispose event listener for Device identifier events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, DeviceIdentifierEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<DeviceIdentifierEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateDeviceIdentifier(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeDeviceIdentifier(event.content().getId());
                    break;
            }
        }
    }

    private void updateDeviceIdentifier(String deviceIdentifierId, DeviceIdentifierEvent event) {
        final String eventType = event.toString().toLowerCase();
        LOGGER.info("Domain {} has received {} Device identifier event for {}", domain.getName(), eventType, deviceIdentifierId);
        deviceIdentifierService.findById(deviceIdentifierId).subscribe(
                this::updateDeviceIdentifier,
                error -> LOGGER.error("Unable to load Device identifier for domain {}", domain.getName(), error),
                () -> LOGGER.error("No Device identifier found with id {}", deviceIdentifierId));
    }

    private void removeDeviceIdentifier(String pluginId) {
        LOGGER.info("Domain {} has received event, remove Device identifier {}", domain.getName(), pluginId);
        deviceIdentifiers.remove(pluginId);
        providers.remove(pluginId);
        domainReadinessService.pluginUnloaded(domain.getId(), pluginId);
    }

    private void updateDeviceIdentifier(DeviceIdentifier detection) {
        domainReadinessService.initPluginSync(domain.getId(), detection.getId(), Type.DEVICE_IDENTIFIER.name());
        try {
            if (needDeployment(detection)) {
                var providerConfig = new ProviderConfiguration(detection.getType(), detection.getConfiguration());
                var provider = deviceIdentifierPluginManager.create(providerConfig);
                this.deviceIdentifiers.put(detection.getId(), detection);
                this.providers.put(detection.getId(), provider);
                LOGGER.info("Device identifier {} loaded for domain {}", detection.getName(), domain.getName());
                domainReadinessService.pluginLoaded(domain.getId(), detection.getId());
            } else {
                LOGGER.info("Device identifier {} already loaded for domain {}", detection.getName(), domain.getName());
                domainReadinessService.pluginLoaded(domain.getId(), detection.getId());
            }
        } catch (Exception ex) {
            this.providers.remove(detection.getId());
            LOGGER.error("Unable to create Device identifier provider for domain {}", domain.getName(), ex);
            domainReadinessService.pluginFailed(domain.getId(), detection.getId(), ex.getMessage());
        }
    }

    @Override
    public Map<String, ?> getTemplateVariables(Client client) {
        Map<String, Object> variables = new HashMap<>();
        var rememberDeviceSettings = getRememberDeviceSettings(client);
        variables.put(REMEMBER_DEVICE_IS_ACTIVE, nonNull(rememberDeviceSettings) && !isNullOrEmpty(rememberDeviceSettings.getDeviceIdentifierId()) && rememberDeviceSettings.isActive());
        if (TRUE.equals(variables.get(REMEMBER_DEVICE_IS_ACTIVE))) {
            var rememberDevice = this.deviceIdentifiers.get(rememberDeviceSettings.getDeviceIdentifierId());
            var provider = this.providers.get(rememberDeviceSettings.getDeviceIdentifierId());
            if(nonNull(provider) && nonNull(rememberDevice)) {
                provider.addConfigurationVariables(variables, rememberDevice.getConfiguration());
            }
        }
        return variables;
    }

    private static RememberDeviceSettings getRememberDeviceSettings(Client client) {
        var mfaSettings = ofNullable(client).orElse(new Client()).getMfaSettings();
        return ofNullable(mfaSettings).orElse(new MFASettings()).getRememberDevice();
    }

    @Override
    public boolean useCookieBasedDeviceIdentifier(Client client) {
        var rememberDeviceSettings = getRememberDeviceSettings(client);
        return this.providers.get(rememberDeviceSettings.getDeviceIdentifierId()).useCookieToKeepIdentifier();
    }

    /**
     * @param deviceIdentifier
     * @return true if the BotDetection has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(DeviceIdentifier deviceIdentifier) {
        final DeviceIdentifier deployedPlugin = this.deviceIdentifiers.get(deviceIdentifier.getId());
        return (deployedPlugin == null || deployedPlugin.getUpdatedAt().before(deviceIdentifier.getUpdatedAt()));
    }
}
