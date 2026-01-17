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
package io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ExtensionGrantEvent;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.ExtensionGrantGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.ExtensionGrantGranterV2;
import io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant.TokenExchangeExtensionGrantGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantProviderConfiguration;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantManagerImpl extends AbstractService implements ExtensionGrantManager, InitializingBean, EventListener<ExtensionGrantEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionGrantManagerImpl.class);
    private final TokenRequestResolver tokenRequestResolver = new TokenRequestResolver();
    private final ConcurrentMap<String, ExtensionGrant> extensionGrants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ExtensionGrantGranter> extensionGrantGranters = new ConcurrentHashMap<>();
    private Date minDate;

    @Autowired
    private Domain domain;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private TokenGranter tokenGranter;

    @Autowired
    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private UserGatewayService userService;

    @Autowired
    private ScopeManager scopeManager;

    @Autowired
    private ProtectedResourceManager protectedResourceManager;

    @Autowired
    private RulesEngine rulesEngine;

    @Autowired
    private SubjectManager subjectManager;

    @Autowired
    private DomainReadinessService domainReadinessService;

    @Autowired
    private AuditService auditService;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing extension grants for domain {}", domain.getName());
        this.tokenRequestResolver.setManagers(this.scopeManager, this.protectedResourceManager);
        extensionGrantRepository.findByDomain(domain.getId())
                .subscribe(
                        extensionGrant -> {
                            // backward compatibility, get the oldest extension grant to set the good one for the old clients
                            if (minDate == null) {
                                minDate = extensionGrant.getCreatedAt();
                            } else if (minDate.after(extensionGrant.getCreatedAt())) {
                                minDate = extensionGrant.getCreatedAt();
                            }
                            updateExtensionGrantProvider(extensionGrant);
                            logger.info("Extension grants loaded for domain {}", domain.getName());
                        },
                        error -> {
                            logger.error("Unable to initialize extension grants for domain {}", domain.getName(), error);
                            domainReadinessService.pluginInitFailed(domain.getId(), Type.EXTENSION_GRANT.name(), error.getMessage());
                        });

        logger.info("Register event listener for extension grant events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ExtensionGrantEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for extension grant events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ExtensionGrantEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<ExtensionGrantEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateExtensionGrant(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeExtensionGrant(event.content().getId());
                    domainReadinessService.pluginUnloaded(domain.getId(), event.content().getId());
                    break;
            }
        }
    }

    private void updateExtensionGrant(String extensionGrantId, ExtensionGrantEvent extensionGrantEvent) {
        final String eventType = extensionGrantEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} extension grant event for {}", domain.getName(), eventType, extensionGrantId);
        extensionGrantRepository.findById(extensionGrantId)
                .subscribe(
                        extensionGrant -> {
                            // backward compatibility, get the oldest extension grant to set the good one for the old clients
                            if (extensionGrants.isEmpty()) {
                                minDate = extensionGrant.getCreatedAt();
                            }
                            updateExtensionGrantProvider(extensionGrant);
                            logger.info("Extension grant {} {}d for domain {}", extensionGrantId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} extension grant for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No extension grant found with id {}", extensionGrantId));
    }

    private void removeExtensionGrant(String extensionGrantId) {
        logger.info("Domain {} has received extension grant event, delete extension grant {}", domain.getName(), extensionGrantId);
        ((CompositeTokenGranter) tokenGranter).removeTokenGranter(extensionGrantId);
        extensionGrants.remove(extensionGrantId);
        extensionGrantGranters.remove(extensionGrantId);
        // backward compatibility, update remaining granters for the min date
        if (!extensionGrants.isEmpty()) {
            minDate = Collections.min(extensionGrants.values().stream().map(ExtensionGrant::getCreatedAt).collect(Collectors.toList()));
            extensionGrantGranters.values().forEach(extensionGrantGranter -> extensionGrantGranter.setMinDate(minDate));
        }
    }

    private void updateExtensionGrantProvider(ExtensionGrant extensionGrant) {
        domainReadinessService.initPluginSync(domain.getId(), extensionGrant.getId(), Type.EXTENSION_GRANT.name());
        try {
            if (needDeployment(extensionGrant)) {
                AuthenticationProvider authenticationProvider = null;
                if (extensionGrant.getIdentityProvider() != null) {
                    logger.info("\tLooking for extension grant identity provider: {}", extensionGrant.getIdentityProvider());
                    authenticationProvider = identityProviderManager.get(extensionGrant.getIdentityProvider()).blockingGet();
                    if (authenticationProvider != null) {
                        logger.info("\tExtension grant identity provider: {}, loaded", extensionGrant.getIdentityProvider());
                    }
                }

                var providerConfiguration = new ExtensionGrantProviderConfiguration(extensionGrant, authenticationProvider);
                var extensionGrantProvider = extensionGrantPluginManager.create(providerConfiguration);
                var extensionGrantGranter = buildGranter(extensionGrant, extensionGrantProvider);
                // backward compatibility, set min date to the extension grant granter to choose the good one for the old clients
                extensionGrantGranter.setMinDate(minDate);
                ((CompositeTokenGranter) tokenGranter).addTokenGranter(extensionGrant.getId(), extensionGrantGranter);
                extensionGrants.put(extensionGrant.getId(), extensionGrant);
                extensionGrantGranters.put(extensionGrant.getId(), extensionGrantGranter);
                domainReadinessService.pluginLoaded(domain.getId(), extensionGrant.getId());
            } else {
                logger.info("Extension grant {} already loaded for domain {}", extensionGrant.getId(), domain.getName());
                domainReadinessService.pluginLoaded(domain.getId(), extensionGrant.getId());
            }
        } catch (Exception ex) {
            // failed to load the plugin
            logger.error("An error occurs while initializing the extension grant : {}", extensionGrant.getName(), ex);
            removeExtensionGrant(extensionGrant.getId());
            domainReadinessService.pluginFailed(domain.getId(), extensionGrant.getId(), ex.getMessage());
        }
    }

    private ExtensionGrantGranter buildGranter(ExtensionGrant extensionGrant, ExtensionGrantProvider extensionGrantProvider) {
        // Use specialized granter for Token Exchange (RFC 8693)
        if (TokenExchangeExtensionGrantGranter.isTokenExchangeGrantType(extensionGrant.getGrantType())) {
            logger.info("\tCreating Token Exchange Extension Grant Granter for: {}", extensionGrant.getName());
            return new TokenExchangeExtensionGrantGranter(
                    extensionGrantProvider,
                    extensionGrant,
                    userAuthenticationManager,
                    tokenService,
                    tokenRequestResolver,
                    identityProviderManager,
                    userService,
                    rulesEngine,
                    domain,
                    auditService);
        }

        if (domain.getVersion() == DomainVersion.V1_0) {
            return new ExtensionGrantGranter(
                            extensionGrantProvider,
                            extensionGrant,
                            userAuthenticationManager,
                            tokenService,
                            tokenRequestResolver,
                            identityProviderManager,
                            userService,
                            rulesEngine,
                            domain);
        } else {
            return new ExtensionGrantGranterV2(
                    extensionGrantProvider,
                    extensionGrant,
                    userAuthenticationManager,
                    tokenService,
                    tokenRequestResolver,
                    identityProviderManager,
                    userService,
                    rulesEngine,
                    subjectManager,
                    domain);
        }
    }

    /**
     * @param extensionGrant
     * @return true if the ExtensionGrant has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(ExtensionGrant extensionGrant) {
        final ExtensionGrant deployedExtensionGrant = this.extensionGrants.get(extensionGrant.getId());
        return (deployedExtensionGrant == null || deployedExtensionGrant.getUpdatedAt().before(extensionGrant.getUpdatedAt()));
    }
}
