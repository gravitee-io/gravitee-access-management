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
package io.gravitee.am.gateway.services.sync.spring;

import io.gravitee.am.gateway.services.sync.SyncManager;
import io.gravitee.am.gateway.services.sync.api.DomainReadinessHandler;
import io.gravitee.am.gateway.services.sync.healthcheck.SyncProbe;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.DomainReadinessServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class SyncConfiguration {

    @Bean
    public SyncManager syncStateManager() {
        return new SyncManager();
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("sync-");
        return scheduler;
    }

    @Bean
    public SyncProbe syncProbe() {
        return new SyncProbe();
    }

    @Bean
    public DomainReadinessHandler domainReadinessHandler() {
        return new DomainReadinessHandler();
    }

    @Bean
    public DomainReadinessRouteConfigurer domainReadinessRouteConfigurer(io.gravitee.am.gateway.reactor.Reactor reactor, DomainReadinessHandler handler) {
        return new DomainReadinessRouteConfigurer(reactor, handler);
    }

    public static class DomainReadinessRouteConfigurer implements org.springframework.beans.factory.InitializingBean {
        private final io.gravitee.am.gateway.reactor.Reactor reactor;
        private final DomainReadinessHandler handler;

        public DomainReadinessRouteConfigurer(io.gravitee.am.gateway.reactor.Reactor reactor, DomainReadinessHandler handler) {
            this.reactor = reactor;
            this.handler = handler;
        }

        @Override
        public void afterPropertiesSet() {
            reactor.route().get("/_internal/domains").handler(handler);
            reactor.route().get("/_internal/domains/:domainId").handler(handler);
        }
    }
}
