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
package io.gravitee.am.gateway.core.email.impl;

import io.gravitee.am.gateway.core.email.EmailStagingStateProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.gateway.api.EmailStagingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class EmailStagingStateProviderImpl implements EmailStagingStateProvider, InitializingBean, DisposableBean {

    private final Map<String, Boolean> domainsToProcess = new ConcurrentHashMap<>();

    @Value("${email.enabled:false}")
    private boolean emailEnabled = false;

    @Value("${email.bulk.enabled:false}")
    private boolean bulkEnabled = false;

    @Value("${email.bulk.period:" + EmailStagingStateProvider.DEFAULT_PERIOD_IN_SECONDS + "}")
    private int period = EmailStagingStateProvider.DEFAULT_PERIOD_IN_SECONDS;

    @Autowired
    @Lazy
    private EmailStagingRepository emailStagingRepository;

    private ScheduledExecutorService scheduler;

    @Override
    public void afterPropertiesSet() {
        if (emailEnabled && bulkEnabled) {
            log.info("Email staging state refresh scheduled every {} seconds", period);
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "email-staging-refresh");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::refreshDomainsToProcess, 0, period, TimeUnit.SECONDS);
        }
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public boolean hasEmailsToProcess(String domain) {
        return domainsToProcess.getOrDefault(domain, false);
    }

    private void refreshDomainsToProcess() {
        try {
            emailStagingRepository.listReferences()
                    .filter(ref -> ref.type() == ReferenceType.DOMAIN)
                    .map(ref -> ref.id())
                    .toList()
                    .subscribe(
                            domainIds -> {
                                var newDomainIds = new java.util.HashSet<>(domainIds);
                                domainsToProcess.keySet().removeIf(d -> !newDomainIds.contains(d));
                                newDomainIds.forEach(d -> domainsToProcess.put(d, true));
                            },
                            err -> log.error("Error refreshing email staging domains to process", err)
                    );
        } catch (NoSuchBeanDefinitionException ex) {
            log.warn("EmailStagingRepository not yet initialized");
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.error("Error refreshing email staging domains to process", ex);
            } else {
                log.error("Error refreshing email staging domains to process: {}", ex.getMessage());
            }
        }
    }
}