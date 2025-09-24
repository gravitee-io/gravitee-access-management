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

import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.InMemoryIdentityProviderListener;
import io.gravitee.am.management.service.impl.utils.InlineOrganizationProviderConfiguration;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.InvalidDataSourceException;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.util.EnvironmentUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.util.JSONObjectUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.impl.utils.InlineOrganizationProviderConfiguration.MEMORY_TYPE;
import static java.util.Optional.ofNullable;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderManagerImpl extends AbstractService<IdentityProviderManager> implements IdentityProviderManager, EventListener<IdentityProviderEvent, Payload> {
    public static final String IDP_GRAVITEE = "gravitee";

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);

    private final ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdentityProvider> identityProviders = new ConcurrentHashMap<>();

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private Environment environment;

    @Autowired
    private RoleService roleService;

    private InMemoryIdentityProviderListener listener;

    public void setListener(InMemoryIdentityProviderListener listener) {
        this.listener = listener;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for identity provider events for the management API");
        eventManager.subscribeForEvents(this, IdentityProviderEvent.class);

        logger.info("Initializing user providers");

        identityProviderService.findAll()
                .flatMapMaybe(identityProvider -> {
                    logger.info("\tInitializing user provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
                    return loadUserProvider(identityProvider);
                }).ignoreElements()
                .andThen(Completable.defer(this::loadIdentityProviders))
                .blockingAwait();

    }

    @Override
    public void onEvent(Event<IdentityProviderEvent, Payload> event) {
        if (Objects.requireNonNull(event.type()) == IdentityProviderEvent.UNDEPLOY) {
            removeUserProvider(event.content().getId());
        } else {
            logger.debug("{} event received for IdentityProvider {}, ignore it as it will be loaded on demand", event.type(), event.content().getId());
        }
    }

    @Override
    public Completable loadIdentityProviders() {
        if (this.listener != null) {
            final IdentityProvider graviteeIdp = buildOrganizationUserIdentityProvider();

            return loadProvidersFromConfig()
                    // add the Gravitee provider to allow addition of OrganizationUser through the console
                    .mergeWith(Single.just(graviteeIdp))
                    .doOnNext(listener::registerAuthenticationProvider)
                    // load gravitee idp into this component to allow user creation and update
                    .flatMapMaybe(this::loadUserProvider)
                    .ignoreElements();
        }
        return Completable.complete();
    }

    private Flowable<IdentityProvider> loadProvidersFromConfig() {
        boolean found = true;
        int idx = 0;

        Flowable<IdentityProvider> identityProvidersFlow = Flowable.empty();

        while (found) {
            String type = environment.getProperty("security.providers[" + idx + "].type");
            found = (type != null);
            if (found) {
                if (type.equals(MEMORY_TYPE)) {
                    InlineOrganizationProviderConfiguration providerConfig = new InlineOrganizationProviderConfiguration(roleService, environment, idx);
                    if (providerConfig.isEnabled()) {
                        identityProvidersFlow = identityProvidersFlow.mergeWith(providerConfig.buildIdentityProvider());
                    }
                } else {
                    logger.warn("Unsupported provider with type '{}'", type);
                }
            }
            idx++;
        }

        return identityProvidersFlow;
    }

    private IdentityProvider buildOrganizationUserIdentityProvider() {
        IdentityProvider provider = new IdentityProvider();
        provider.setId(IDP_GRAVITEE);
        provider.setExternal(false);
        provider.setType("gravitee-am-idp");
        provider.setName(IDP_GRAVITEE);
        provider.setReferenceId(Organization.DEFAULT);
        provider.setReferenceType(ReferenceType.ORGANIZATION);
        provider.setConfiguration("{}");
        return provider;
    }

    @Override
    public Maybe<UserProvider> getUserProvider(String userProvider) {
        if (userProvider == null) {
            return Maybe.empty();
        }

        if (IDP_GRAVITEE.equals(userProvider) && userProviders.containsKey(userProvider)) {
            // The gravitee idp isn't persisted so before continuing,
            // we try to get it from the map of providers
            // if missing we switch to the default behaviour just in case
            return Maybe.just(userProviders.get(userProvider));
        }

        // Since https://github.com/gravitee-io/issues/issues/6590 we have to read the record in Identity Provider repository
        return identityProviderService.findById(userProvider)
                .flatMap(persistedUserProvider -> {
                    UserProvider localUserProvider = userProviders.get(userProvider);
                    if (localUserProvider != null &&
                            identityProviders.containsKey(userProvider) &&
                            identityProviders.get(userProvider).getUpdatedAt().getTime() >= persistedUserProvider.getUpdatedAt().getTime()) {
                        return Maybe.just(localUserProvider);
                    } else {
                        this.removeUserProvider(userProvider);
                        return this.loadUserProvider(persistedUserProvider);
                    }
                });
    }

    @Override
    public Optional<IdentityProvider> getIdentityProvider(String providerId) {
        return ofNullable(identityProviders.get(providerId));
    }

    private void removeUserProvider(String identityProviderId) {
        logger.info("Management API has received a undeploy identity provider event for {}", identityProviderId);
        UserProvider userProvider = userProviders.remove(identityProviderId);
        identityProviders.remove(identityProviderId);
        if (userProvider != null) {
            // stop the user provider
            try {
                userProvider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the user provider : {}", identityProviderId, e);
            }
        }
    }

    private Maybe<UserProvider> loadUserProvider(IdentityProvider identityProvider) {
        return identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration(), identityProvider)
                .flatMapMaybe(userProviderOpt -> {
                    if (userProviderOpt.isPresent()) {
                        userProviders.put(identityProvider.getId(), userProviderOpt.get());
                        identityProviders.put(identityProvider.getId(), identityProvider);
                        return Maybe.just(userProviderOpt.get());
                    } else {
                        userProviders.remove(identityProvider.getId());
                        identityProviders.remove(identityProvider.getId());
                        return Maybe.empty();
                    }
                }).onErrorResumeNext(ex -> {
                    logger.error("An error has occurred while loading user provider: {} [{}]", identityProvider.getName(), identityProvider.getType(), ex);
                    userProviders.remove(identityProvider.getId());
                    identityProviders.remove(identityProvider.getId());
                    return Maybe.empty();
                });
    }

    public Completable checkPluginDeployment(String type) {
        if (!this.identityProviderPluginManager.isPluginDeployed(type)) {
            logger.debug("Plugin {} not deployed", type);
            return Completable.error(PluginNotDeployedException.forType(type));
        }
        return Completable.complete();
    }
    
    public Completable validateDatasource(String configuration) {
        if (configuration == null) {
            return Completable.complete();
        }

        try {
            Map<String, Object> cfg = JSONObjectUtils.parse(configuration);
            String datasourceId = (String) cfg.getOrDefault("datasourceId", "");

            if (datasourceId.isEmpty()) {
                return Completable.complete();
            }
            
            return validateDatasourceId(datasourceId) ?
                Completable.complete() : 
                Completable.error(new InvalidDataSourceException(String.format("Datasource with ID %s not found", datasourceId)));

        } catch (ParseException e) {
            logger.warn("Unable to parse configuration for identity provider", e);
            return Completable.complete();
        }
    }

    private boolean validateDatasourceId(String datasourceId) {
        for (String key : getDatasourceIdentifierKeys()) {
            String foundDatasourceId = environment.getProperty(key, String.class);

            if (datasourceId.equals(foundDatasourceId)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getDatasourceIdentifierKeys() {
        return EnvironmentUtils
                .getPropertiesStartingWith((ConfigurableEnvironment) environment, "datasources.")
                .keySet()
                .stream()
                .map(String::valueOf)
                .filter(value -> value.contains(".id"))
                .collect(Collectors.toList());
    }
}
