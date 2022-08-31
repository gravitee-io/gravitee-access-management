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
package io.gravitee.am.gateway.handler.manager.dictionary;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.I18nDictionaryEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.I18nDictionaryRepository;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
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
public class I18nDictionaryManager extends AbstractService implements InitializingBean, EventListener<I18nDictionaryEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(I18nDictionaryManager.class);
    private ConcurrentMap<String, I18nDictionary> i18nDictionaries = new ConcurrentHashMap<>();

    @Autowired
    private I18nDictionaryRepository i18nDictionaryRepository;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private GraviteeMessageResolver graviteeMessageResolver;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing i18n dictionaries for domain {}", domain.getName());
        i18nDictionaryRepository.findAll(ReferenceType.DOMAIN, domain.getId())
                .subscribe(
                        i18nDictionary -> {
                            update(i18nDictionary);
                            logger.info("I18n dictionary {} loaded for domain {}", i18nDictionary.getId(), domain.getName());
                        },
                        error -> logger.error("Unable to initialize i18n dictionaries for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for i18n dictionary events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, I18nDictionaryEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for i18n dictionary events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, I18nDictionaryEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<I18nDictionaryEvent, Payload> event) {
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

    private void update(String id, I18nDictionaryEvent event) {
        final String eventType = event.toString().toLowerCase();
        logger.info("Domain {} has received {} i18n dictionary event for {}", domain.getName(), eventType, id);
        i18nDictionaryRepository.findById(id)
                .subscribe(
                        i18nDictionary -> {
                            update(i18nDictionary);
                            logger.info("I18n dictionary {} {}d for domain {}", id, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} i18n dictionary for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No i18n dictionary found with id {}", id));
    }

    private void update(I18nDictionary i18nDictionary) {
        i18nDictionaries.put(i18nDictionary.getId(), i18nDictionary);
        graviteeMessageResolver.updateDictionary(i18nDictionary);
    }

    private void remove(String id) {
        logger.info("Domain {} has received i18n dictionary event, delete i18n dictionary {}", domain.getName(), id);
        I18nDictionary deletedI18nDictionary = i18nDictionaries.remove(id);
        if (deletedI18nDictionary != null) {
            graviteeMessageResolver.removeDictionary(deletedI18nDictionary.getLocale());
        }
    }
}
