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
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import static io.gravitee.am.identityprovider.api.common.IdentityProviderConfigurationUtils.extractCertificateId;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderManagerImpl extends AbstractService implements IdentityProviderManager, EventListener<IdentityProviderEvent, Payload>, InitializingBean {

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

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

    @Autowired
    private DomainReadinessService domainReadinessService;

    private final ConcurrentMap<String, AuthenticationProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdentityProvider> identities = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> idLocks = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(String id) {
        return idLocks.computeIfAbsent(id, k -> new ReentrantLock());
    }

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
                    .concatMapSingle(this::updateAuthenticationProvider)
                    .map(provider -> {
                        gatewayMetricProvider.incrementIdp();
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
            gatewayMetricProvider.incrementIdpEvt();
            switch (event.type()) {
                case DEPLOY -> {
                    gatewayMetricProvider.incrementIdp();
                    updateIdentityProvider(event.content().getId(), event.type());
                }
                case UPDATE -> updateIdentityProvider(event.content().getId(), event.type());
                case UNDEPLOY -> {
                    removeIdentityProvider(event.content().getId());
                    gatewayMetricProvider.decrementIdp();
                }
                default -> {
                    // No action needed for default case
                }
            }
        }
    }

    private void updateIdentityProvider(String identityProviderId, IdentityProviderEvent identityProviderEvent) {
        final String eventType = identityProviderEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} identity provider event for {}", domain.getName(), eventType, identityProviderId);
        identityProviderRepository.findById(identityProviderId)
                .flatMapSingle(this::updateAuthenticationProvider)
                .subscribe(
                        identityProvider -> {
                            logger.info("Identity provider {} {} for domain {}", identityProviderId, eventType, domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), identityProviderId);
                        },
                        error -> {
                            logger.error("Unable to {} identity provider for domain {}", eventType, domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), identityProviderId, error.getMessage());
                        },
                        () -> logger.error("No identity provider found with id {}", identityProviderId));
    }

    private void removeIdentityProvider(String identityProviderId) {
        logger.info("Domain {} has received identity provider event, delete identity provider {}", domain.getName(), identityProviderId);
        clearProvider(identityProviderId);
    }

    private Single<IdentityProvider> updateAuthenticationProvider(IdentityProvider identityProvider) {
        domainReadinessService.initPluginSync(domain.getId(), identityProvider.getId(), Type.IDENTITY_PROVIDER.name());
        if (needDeployment(identityProvider)) {
            return forceUpdateAuthenticationProvider(identityProvider);
        } else {
            logger.debug("Identity provider already initialized: {} [{}]", identityProvider.getName(), identityProvider.getType());
            return Single.just(identityProvider);
        }
    }

    private Single<IdentityProvider> forceUpdateAuthenticationProvider(IdentityProvider identityProvider) {
        String identityProviderId = identityProvider.getId();
        try {
            if (hasExistingProvider(identityProviderId)) {
                return redeployProvider(identityProvider).doOnError(error -> logger.error("An error occurs while redeploying the identity provider : {}", identityProvider.getName(), error));
            } else {
                return deployProvider(identityProvider).doOnError(error -> logger.error("An error occurs while initializing the identity provider : {}", identityProvider.getName(), error));
            }
        } catch (Exception ex) {
            logger.error("An error occurs while initializing the identity provider: {}", identityProvider.getName(), ex);
            return Single.error(ex);
        }

    }

    private void clearProviders() {
        providers.keySet().forEach(this::clearProvider);
    }

    private Single<Providers> createProvider(IdentityProvider identityProvider) throws IOException {
        var authProviderConfig = new AuthenticationProviderConfiguration(identityProvider, certificateManager);
        try (var authenticationProvider = identityProviderPluginManager.create(authProviderConfig)) {
            if (authenticationProvider == null) {
                logger.error("Failed to create authentication provider for {}", identityProvider.getName());
                return Single.error(new Exception("Failed to create authentication provider for " + identityProvider.getName()));

            } else {
                return identityProviderPluginManager
                        .create(identityProvider.getType(), identityProvider.getConfiguration(), identityProvider)
                        .map(userProviderOpt -> new Providers(identityProvider.getId(), authenticationProvider,
                                userProviderOpt.orElse(null), identityProvider));                 
            }
        }
    }

    private Providers atomicReplaceProviders(String id, Providers newProviders) {
        var old = Providers.fromCurrent(id, providers, userProviders, identities);

        if (newProviders.authProvider != null) providers.put(id, newProviders.authProvider);
        else providers.remove(id);

        if (newProviders.userProvider != null) userProviders.put(id, newProviders.userProvider);
        else userProviders.remove(id);

        identities.put(id, newProviders.identity);
        return old;
    }

    private Completable stopProviders(Providers oldProviders) {
        return Completable.fromAction(() -> {
            if (oldProviders.authProvider != null) {
                try {
                    domainReadinessService.pluginUnloaded(domain.getId(), oldProviders.identity.getId());
                    oldProviders.authProvider.stop();
                    logger.debug("Stopped old authentication provider after replacement");
                } catch (Exception e) {
                    logger.warn("Error stopping old authentication provider after replacement", e);
                }
            }
            if (oldProviders.userProvider != null) {
                try {
                    oldProviders.userProvider.stop();
                    logger.debug("Stopped old user provider after replacement");
                } catch (Exception e) {
                    logger.warn("Error stopping old user provider after replacement", e);
                }
            }
        });
    }

    private boolean hasExistingProvider(String identityProviderId) {
        return providers.containsKey(identityProviderId) ||
                userProviders.containsKey(identityProviderId) ||
                identities.containsKey(identityProviderId);
    }

    private Single<IdentityProvider> deployProvider(IdentityProvider identityProvider) {
        logger.info("Deploying new identity provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
        final String id = identityProvider.getId();
        final ReentrantLock lock = lockFor(id);

        domainReadinessService.initPluginSync(domain.getId(), id, Type.IDENTITY_PROVIDER.name());

        try {
            return createProvider(identityProvider)
                    .map(newProviders -> {
                        try {
                            lock.lock();
                            if (newProviders.authProvider != null) {
                                providers.put(identityProvider.getId(), newProviders.authProvider);
                            }
                            identities.put(identityProvider.getId(), identityProvider);
                            if (newProviders.userProvider != null) {
                                userProviders.put(identityProvider.getId(), newProviders.userProvider);
                            }
                        } finally {
                            lock.unlock();
                        }
                        logger.info("Successfully deployed new identity provider {} for domain {}", identityProvider.getId(), domain.getName());
                        return identityProvider;
                    })
                    .doOnSuccess(idp -> domainReadinessService.pluginLoaded(domain.getId(), idp.getId()))
                    .doOnError(e -> {
                        logger.error("Failed to deploy identity provider {}", id, e);
                        domainReadinessService.pluginFailed(domain.getId(), id, e.getMessage());
                    });
        } catch (IOException e) {
            logger.error("Failed to deploy identity provider {}", id, e);
            domainReadinessService.pluginFailed(domain.getId(), id, e.getMessage());
            return Single.error(e);
        }
    }

    private Single<IdentityProvider> redeployProvider(IdentityProvider identityProvider) throws IOException {
        logger.info("Replacing existing identity provider: {} [{}]", identityProvider.getName(), identityProvider.getType());

        final String id = identityProvider.getId();
        final ReentrantLock lock = lockFor(id);

        domainReadinessService.initPluginSync(domain.getId(), id, Type.IDENTITY_PROVIDER.name());

        return createProvider(identityProvider)
                .flatMap(newProviders -> {
                    Providers old;
                    lock.lock();
                    try {
                        old = atomicReplaceProviders(id, newProviders);
                    } finally {
                        lock.unlock();
                    }
                    return stopProviders(old)
                            .subscribeOn(Schedulers.io())
                            .onErrorComplete()
                            .andThen(Single.just(identityProvider));
                })
                .doOnSuccess(idp -> domainReadinessService.pluginLoaded(domain.getId(), idp.getId()))
                .doOnError(e -> {
                    logger.error("Failed to replace identity provider {}", id, e);
                    domainReadinessService.pluginFailed(domain.getId(), id, e.getMessage());
                });
    }

    private record Providers(String id, AuthenticationProvider authProvider, UserProvider userProvider,
                             IdentityProvider identity) {

        static Providers fromCurrent(String identityProviderId,
                                     ConcurrentMap<String, AuthenticationProvider> providers,
                                     ConcurrentMap<String, UserProvider> userProviders,
                                     ConcurrentMap<String, IdentityProvider> identities) {
            return new Providers(identityProviderId,
                    providers.get(identityProviderId),
                    userProviders.get(identityProviderId),
                    identities.get(identityProviderId));
        }
    }

    private void clearProvider(String identityProviderId) {
        AuthenticationProvider authenticationProvider = providers.remove(identityProviderId);
        UserProvider userProvider = userProviders.remove(identityProviderId);
        identities.remove(identityProviderId);
        domainReadinessService.pluginUnloaded(domain.getId(), identityProviderId);
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

    /**
     * @param provider the {@link IdentityProvider} to check for deployment
     * @return {@code true} if the IDP has never been deployed or if the deployed version is not up to date,
     *         {@code false} otherwise
     */
    private boolean needDeployment(IdentityProvider provider) {
        final IdentityProvider deployedProvider = this.identities.get(provider.getId());
        return (deployedProvider == null || deployedProvider.getUpdatedAt().before(provider.getUpdatedAt()));
    }

    @Override
    public Completable reloadIdentityProvidersWithCertificate(String certificateId) {
        var publisher = identityProviderRepository
                .findAll()
                .filter(idp -> extractCertificateId(idp.getConfiguration())
                        .map(idpCertId -> idpCertId.equals(certificateId))
                        .orElse(false))
                .doOnNext(idp -> logger.info("Identity provider id={} from domain {} needs to be reloaded.", idp.getId(), domain.getName()))
                .flatMapSingle(this::forceUpdateAuthenticationProvider);
        return Completable.fromPublisher(publisher);
    }
}
