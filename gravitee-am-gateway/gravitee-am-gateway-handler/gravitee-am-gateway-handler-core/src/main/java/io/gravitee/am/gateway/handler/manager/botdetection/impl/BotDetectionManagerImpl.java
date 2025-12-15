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
package io.gravitee.am.gateway.handler.manager.botdetection.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.botdetection.api.BotDetectionContext;
import io.gravitee.am.botdetection.api.BotDetectionProvider;
import io.gravitee.am.common.event.BotDetectionEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.DomainState;
import io.gravitee.am.plugins.botdetection.core.BotDetectionPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.service.BotDetectionService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.gravitee.am.common.utils.ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION;
import static io.gravitee.am.common.utils.ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionManagerImpl extends AbstractService implements BotDetectionManager, InitializingBean,EventListener<BotDetectionEvent, Payload> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotDetectionManagerImpl.class);

    public static final String TEMPLATE_KEY_BOT_DETECTION_ENABLED = "bot_detection_enabled";

    private final ConcurrentMap<String, BotDetectionProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BotDetection> botDetections = new ConcurrentHashMap<>();

    @Autowired
    private BotDetectionService botDetectionService;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private BotDetectionPluginManager botDetectionPluginManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainReadinessService domainReadinessService;

    public ConcurrentMap<String, BotDetection> getBotDetections() {
        return botDetections;
    }

    @Override
    public void afterPropertiesSet() {
        LOGGER.info("Initializing bot detections for domain {}", domain.getName());
        botDetectionService.findByDomain(domain.getId())
                .subscribe(
                        detection -> {
                            updateBotDetection(detection);
                            LOGGER.info("Bot detection {} loaded for domain {}", detection.getName(), domain.getName());
                        },
                        error -> {
                            LOGGER.error("Unable to initialize bot detections for domain {}", domain.getName(), error);
                            domainReadinessService.pluginInitFailed(domain.getId(), Type.BOT_DETECTION.name(), error.getMessage());
                        });
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOGGER.info("Register event listener for bot detections events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, BotDetectionEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOGGER.info("Dispose event listener for bot detection events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, BotDetectionEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<BotDetectionEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateBotDetection(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeBotDetection(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Single<Boolean> validate(BotDetectionContext context) {
        final BotDetectionProvider botDetectionProvider = this.providers.get(context.getPluginId());
        if (botDetectionProvider != null) {
            return botDetectionProvider.validate(context);
        } else {
            LOGGER.error("No BotDetectionProvider matches the pluginId '{}'", context.getPluginId());
            return Single.just(false);
        }
    }

    @Override
    public Map<String, Object> getTemplateVariables(Domain domain, Client client) {
        Map<String, Object> variables = new HashMap<>();
        AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        variables.put(TEMPLATE_KEY_BOT_DETECTION_ENABLED, accountSettings != null && accountSettings.isUseBotDetection() && accountSettings.getBotDetectionPlugin() != null);
        if (accountSettings != null && accountSettings.isUseBotDetection()) {
            if (accountSettings.getBotDetectionPlugin() == null) {
                LOGGER.warn("Bot Detection enabled but plugin reference isn't defined in settings");
            } else {
                final BotDetection botDetection = this.botDetections.get(accountSettings.getBotDetectionPlugin());
                variables.put(TEMPLATE_KEY_BOT_DETECTION_PLUGIN, botDetection.getType());
                try {
                    variables.put(TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION, objectMapper.readValue(botDetection.getConfiguration(), HashMap.class));
                } catch (JsonProcessingException e) {
                    LOGGER.error("Unable to read the configuration for bot detection '{}'", botDetection.getId(), e);
                    throw new TechnicalManagementException("Unable to read configuration");
                }
            }
        }
        return variables;
    }

    private void updateBotDetection(String pluginId, BotDetectionEvent event) {
        final String eventType = event.toString().toLowerCase();
        LOGGER.info("Domain {} has received {} bot detection event for {}", domain.getName(), eventType, pluginId);
        botDetectionService.findById(pluginId)
                .subscribe(
                        this::updateBotDetection,
                        error -> {
                            LOGGER.error("Unable to load bot detection for domain {}", domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), pluginId, error.getMessage());
                        },
                        () -> {
                            LOGGER.error("No bot detection found with id {}", pluginId);
                            domainReadinessService.pluginFailed(domain.getId(), pluginId, "No bot detection found with id " + pluginId);
                        });
    }

    private void removeBotDetection(String pluginId) {
        LOGGER.info("Domain {} has received event, remove bot detection {}", domain.getName(), pluginId);
        final BotDetectionProvider previousProvider = providers.remove(pluginId);
        botDetections.remove(pluginId);
        stopBotDetectionProvider(previousProvider);
        domainReadinessService.pluginUnloaded(domain.getId(), pluginId);
    }

    private void updateBotDetection(BotDetection detection) {
        domainReadinessService.initPluginSync(domain.getId(), detection.getId(), Type.BOT_DETECTION.name());
        try {
            if (needDeployment(detection)) {
                var providerConfig = new ProviderConfiguration(detection.getType(), detection.getConfiguration());
                BotDetectionProvider botDetectionProvider = botDetectionPluginManager.create(providerConfig);
                final BotDetectionProvider previousProvider = this.providers.put(detection.getId(), botDetectionProvider);
                stopBotDetectionProvider(previousProvider);
                this.botDetections.put(detection.getId(), detection);

                LOGGER.info("Bot detection {} loaded for domain {}", detection.getName(), domain.getName());
                domainReadinessService.pluginLoaded(domain.getId(), detection.getId());
            } else {
                LOGGER.info("Bot detection {} already loaded for domain {}", detection.getName(), domain.getName());
                domainReadinessService.pluginLoaded(domain.getId(), detection.getId());
            }
        } catch (Exception ex) {
            this.providers.remove(detection.getId());
            LOGGER.error("Unable to create bot detection provider for domain {}", domain.getName(), ex);
            domainReadinessService.pluginFailed(domain.getId(), detection.getId(), ex.getMessage());
        }
    }

    private void stopBotDetectionProvider(BotDetectionProvider previousProvider) {
        try {
            if (previousProvider != null) {
                previousProvider.stop();
            }
        } catch (Exception e) {
            LOGGER.error("Unable to stop bot detection provider for domain {}", domain.getName(), e);
        }
    }

    /**
     * @param botDetection
     * @return true if the BotDetection has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(BotDetection botDetection) {
        final BotDetection deployedPlugin = this.botDetections.get(botDetection.getId());
        return (deployedPlugin == null || deployedPlugin.getUpdatedAt().before(botDetection.getUpdatedAt()));
    }
}
