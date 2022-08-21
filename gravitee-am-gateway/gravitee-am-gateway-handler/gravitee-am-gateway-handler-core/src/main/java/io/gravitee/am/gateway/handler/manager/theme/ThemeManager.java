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
package io.gravitee.am.gateway.handler.manager.theme;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ThemeEvent;
import io.gravitee.am.gateway.handler.vertx.view.thymeleaf.DomainBasedThemeResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.ThemeRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeManager extends AbstractService<ThemeManager> implements InitializingBean, EventListener<ThemeEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private ConcurrentMap<String, Theme> domainThemes = new ConcurrentHashMap<>();

    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private DomainBasedThemeResolver domainBasedThemeResolver;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing themes for domain {}", domain.getName());
        themeRepository.findByReference(ReferenceType.DOMAIN, domain.getId())
                .subscribe(
                        theme -> {
                            update(theme);
                            logger.info("Theme {} loaded for domain {}", theme.getId(), domain.getName());
                        },
                        error -> logger.error("Unable to initialize themes for domain {}", domain.getName(), error),
                        () -> logger.debug("No theme found for domain {}", domain.getName()));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for theme events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ThemeEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for theme events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ThemeEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<ThemeEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN &&
                domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    update(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    remove(event.content().getId());
                    break;
            }
        }
    }

    private void update(String id, ThemeEvent event) {
        final String eventType = event.toString().toLowerCase();
        logger.info("Domain {} has received {} theme event for {}", domain.getName(), eventType, id);
        themeRepository.findById(id)
                .subscribe(
                        theme -> {
                            update(theme);
                            logger.info("Theme {} {}d for domain {}", id, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} theme for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No theme found with id {}", id));
    }

    private void update(Theme theme) {
        domainThemes.put(theme.getId(), theme);
        domainBasedThemeResolver.updateTheme(theme);
    }

    private void remove(String id) {
        logger.info("Domain {} has received theme event, delete theme {}", domain.getName(), id);
        Theme deletedTheme = domainThemes.remove(id);
        if (deletedTheme != null) {
            domainBasedThemeResolver.removeTheme(deletedTheme.getReferenceId());
        }
    }
}
