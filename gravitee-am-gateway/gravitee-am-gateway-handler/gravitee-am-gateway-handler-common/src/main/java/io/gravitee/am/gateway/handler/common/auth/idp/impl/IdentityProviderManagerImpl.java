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
package io.gravitee.am.gateway.handler.common.auth.idp.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.metrics.CounterHelper;
import io.gravitee.am.monitoring.metrics.GaugeHelper;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.gravitee.am.monitoring.metrics.Constants.METRICS_IDPS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_IDP_EVENTS;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderManagerImpl extends AbstractService implements IdentityProviderManager, InitializingBean, EventListener<IdentityProviderEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private CertificateManager certificateManager;

    private final CounterHelper idpEvtCounter = new CounterHelper(METRICS_IDP_EVENTS);

    private final GaugeHelper idpGauge = new GaugeHelper(METRICS_IDPS);

    private final ConcurrentMap<String, AuthenticationProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdentityProvider> identities = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();

    @Override
    public Maybe<AuthenticationProvider> get(String id) {
        AuthenticationProvider authenticationProvider = providers.get(id);
        return (authenticationProvider != null) ? Maybe.just(authenticationProvider) : Maybe.empty();
    }

    @Override
    public IdentityProvider getIdentityProvider(String id) {
        return identities.get(id);
    }

    @Override
    public Maybe<UserProvider> getUserProvider(String id) {
        UserProvider userProvider = userProviders.get(id);
        return (userProvider != null) ? Maybe.just(userProvider) : Maybe.empty();
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing identity providers for domain {}", domain.getName());

        try {
            identityProviderRepository.findAll(ReferenceType.DOMAIN, domain.getId())
                    .flatMapSingle(this::updateAuthenticationProvider)
                    .map(provider -> {
                        idpGauge.incrementValue();
                        return provider;
                    })
                    .blockingLast();
            logger.info("Identity providers loaded for domain {}", domain.getName());
        } catch (Exception e) {
            logger.error("Unable to initialize identity providers for domain {}", domain.getName(), e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for identity provider events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, IdentityProviderEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for identity provider events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, IdentityProviderEvent.class, domain.getId());
        clearProviders();
    }

    @Override
    public void onEvent(Event<IdentityProviderEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            idpEvtCounter.increment();
            switch (event.type()) {
                case DEPLOY:
                    idpGauge.incrementValue();
                case UPDATE:
                    updateIdentityProvider(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeIdentityProvider(event.content().getId());
                    idpGauge.decrementValue();
                    break;
            }
        }
    }

    private void updateIdentityProvider(String identityProviderId, IdentityProviderEvent identityProviderEvent) {
        final String eventType = identityProviderEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} identity provider event for {}", domain.getName(), eventType, identityProviderId);
        identityProviderRepository.findById(identityProviderId)
                .flatMapSingle(this::updateAuthenticationProvider)
                .toMaybe()
                .subscribe(
                        identityProvider -> {
                            logger.info("Identity provider {} {}d for domain {}", identityProviderId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} identity provider for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No identity provider found with id {}", identityProviderId));
    }

    private void removeIdentityProvider(String identityProviderId) {
        logger.info("Domain {} has received identity provider event, delete identity provider {}", domain.getName(), identityProviderId);
        clearProvider(identityProviderId);
    }

    private Single<IdentityProvider> updateAuthenticationProvider(IdentityProvider identityProvider) {
        return Single.fromCallable(() -> {
            logger.info("\tInitializing identity provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
            // stop existing provider, if any
            clearProvider(identityProvider.getId());
            return identityProvider;
        }).flatMap(idp -> {
            var authProviderConfig = new AuthenticationProviderConfiguration(identityProvider, certificateManager);
            var authenticationProvider = identityProviderPluginManager.create(authProviderConfig);
            if (authenticationProvider != null) {
                // init the user provider
                return identityProviderPluginManager
                        .create(identityProvider.getType(), identityProvider.getConfiguration(), identityProvider)
                        .map(userProviderOpt -> {
                            providers.put(identityProvider.getId(), authenticationProvider);
                            identities.put(identityProvider.getId(), identityProvider);
                            if (userProviderOpt.isPresent()) {
                                userProviders.put(identityProvider.getId(), userProviderOpt.get());
                            } else {
                                userProviders.remove(identityProvider.getId());
                            }
                            return idp;
                        });
            } else {
                return Single.just(idp);
            }
        }).doOnError(error -> {
            logger.error("An error occurs while initializing the identity provider : {}", identityProvider.getName(), error);
            clearProvider(identityProvider.getId());
        });
    }

    private void clearProviders() {
        providers.keySet().forEach(this::clearProvider);
    }

    private void clearProvider(String identityProviderId) {
        AuthenticationProvider authenticationProvider = providers.remove(identityProviderId);
        UserProvider userProvider = userProviders.remove(identityProviderId);
        identities.remove(identityProviderId);
        if (authenticationProvider != null) {
            // stop the authentication provider
            try {
                authenticationProvider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the authentication provider : {}", identityProviderId, e);
            }
        }
        if (userProvider != null) {
            // stop the user provider
            try {
                userProvider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the user provider : {}", identityProviderId, e);
            }
        }
    }
}
