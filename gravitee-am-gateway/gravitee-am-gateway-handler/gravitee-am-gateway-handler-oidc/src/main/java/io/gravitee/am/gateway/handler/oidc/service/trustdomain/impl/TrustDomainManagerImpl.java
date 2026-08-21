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
package io.gravitee.am.gateway.handler.oidc.service.trustdomain.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.TrustDomainEvent;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.TrustDomainKeyService;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.TrustDomainManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.CustomLog;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@CustomLog
public class TrustDomainManagerImpl extends AbstractService implements TrustDomainManager, InitializingBean, EventListener<TrustDomainEvent, Payload> {

    private static final String PRELOAD_PLUGIN_ID = Type.TRUST_DOMAIN.name();

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private TrustDomainRepository trustDomainRepository;

    @Autowired
    private TrustDomainKeyService trustDomainKeyService;

    @Autowired
    private DomainReadinessService domainReadinessService;

    private final ConcurrentMap<String, TrustDomain> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrustDomain> spiffeByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrustDomain> tokenExchangeByIssuer = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrustDomain> tokenExchangeByName = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() {
        log.info("Initializing trusted domains for domain {}", domain.getName());
        domainReadinessService.initPluginSync(domain.getId(), PRELOAD_PLUGIN_ID, Type.TRUST_DOMAIN.name());
        trustDomainRepository.findByReference(ReferenceType.DOMAIN, domain.getId())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        trustDomain -> {
                            domainReadinessService.initPluginSync(domain.getId(), trustDomain.getId(), Type.TRUST_DOMAIN.name());
                            index(trustDomain);
                            log.info("Trusted domain {} loaded for domain {}", trustDomain.getName(), domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), trustDomain.getId());
                        },
                        error -> {
                            log.error("An error has occurred when loading trusted domains for domain {}", domain.getName(), error);
                            domainReadinessService.pluginInitFailed(domain.getId(), Type.TRUST_DOMAIN.name(), error.getMessage());
                        },
                        () -> {
                            log.info("Trusted domains loaded for domain {}", domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), PRELOAD_PLUGIN_ID);
                        });

        log.info("Register event listener for trusted domain events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, TrustDomainEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        log.info("Dispose event listener for trusted domain events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, TrustDomainEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<TrustDomainEvent, Payload> event) {
        log.debug("Receive trusted domain event {} for id {}", event.type(), event.content().getId());
        if (event.content().getReferenceType() != ReferenceType.DOMAIN
                || !domain.getId().equals(event.content().getReferenceId())) {
            return;
        }
        final String trustDomainId = event.content().getId();
        switch (event.type()) {
            case DEPLOY, UPDATE -> reload(trustDomainId);
            case UNDEPLOY -> unload(trustDomainId);
        }
    }

    @Override
    public Optional<TrustDomain> findSpiffeByName(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(spiffeByName.get(name));
    }

    @Override
    public Optional<TrustDomain> findByIssuer(String issuer) {
        return issuer == null ? Optional.empty() : Optional.ofNullable(tokenExchangeByIssuer.get(issuer));
    }

    @Override
    public Optional<TrustDomain> findTokenExchangeByName(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(tokenExchangeByName.get(name));
    }

    @Override
    public boolean hasTokenExchangeTrust() {
        return !tokenExchangeByIssuer.isEmpty();
    }

    private void reload(String trustDomainId) {
        log.info("Loading trusted domain {} for domain {}", trustDomainId, domain.getName());
        domainReadinessService.initPluginSync(domain.getId(), trustDomainId, Type.TRUST_DOMAIN.name());
        trustDomainRepository.findById(trustDomainId)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        trustDomain -> {
                            index(trustDomain);
                            trustDomainKeyService.evict(trustDomainId);
                            log.info("Trusted domain {} loaded for domain {}", trustDomain.getName(), domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), trustDomainId);
                        },
                        error -> {
                            log.error("An error has occurred when loading trusted domain {} for domain {}", trustDomainId, domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), trustDomainId, error.getMessage());
                        },
                        () -> {
                            log.warn("No trusted domain found with id {} for domain {}", trustDomainId, domain.getName());
                            unload(trustDomainId);
                        });
    }

    private void unload(String trustDomainId) {
        TrustDomain removed = byId.remove(trustDomainId);
        if (removed != null) {
            unindex(removed);
            log.info("Trusted domain {} has been removed for domain {}", removed.getName(), domain.getName());
        }
        trustDomainKeyService.evict(trustDomainId);
        domainReadinessService.pluginRemoved(domain.getId(), trustDomainId);
    }

    private void index(TrustDomain trustDomain) {
        TrustDomain previous = byId.put(trustDomain.getId(), trustDomain);
        if (previous != null) {
            unindex(previous);
        }
        if (trustDomain.getKind() == TrustDomainKind.TOKEN_EXCHANGE) {
            Optional.ofNullable(trustDomain.issuer()).ifPresent(issuer -> tokenExchangeByIssuer.put(issuer, trustDomain));
            if (trustDomain.getName() != null) {
                tokenExchangeByName.put(trustDomain.getName(), trustDomain);
            }
        } else if (trustDomain.getName() != null) {
            spiffeByName.put(trustDomain.getName(), trustDomain);
        }
    }

    private void unindex(TrustDomain trustDomain) {
        if (trustDomain.getName() != null) {
            spiffeByName.remove(trustDomain.getName(), trustDomain);
            tokenExchangeByName.remove(trustDomain.getName(), trustDomain);
        }
        Optional.ofNullable(trustDomain.issuer()).ifPresent(issuer -> tokenExchangeByIssuer.remove(issuer, trustDomain));
    }
}
