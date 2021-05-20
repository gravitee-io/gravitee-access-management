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
package io.gravitee.am.management.handlers.management.api.authentication.manager.form.impl;

import io.gravitee.am.common.event.FormEvent;
import io.gravitee.am.management.handlers.management.api.authentication.manager.form.FormManager;
import io.gravitee.am.management.handlers.management.api.authentication.view.TemplateResolver;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.FormService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
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
public class FormManagerImpl implements FormManager, InitializingBean, EventListener<FormEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(FormManagerImpl.class);
    private ConcurrentMap<String, Form> forms = new ConcurrentHashMap<>();

    @Autowired
    private FormService formService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private TemplateResolver templateResolver;

    @Override
    public void afterPropertiesSet() {
        logger.info("Register event listener for form events");
        eventManager.subscribeForEvents(this, FormEvent.class);

        logger.info("Initializing forms");

        formService.findAll(ReferenceType.ORGANIZATION)
                .subscribe(
                        form -> {
                            updateForm(form);
                            logger.info("Forms loaded");
                        },
                        error -> logger.error("Unable to initialize forms", error));
    }

    @Override
    public void onEvent(Event<FormEvent, Payload> event) {

        if (event.content().getReferenceType() == ReferenceType.ORGANIZATION && event.content().getReferenceId() != null) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateForm(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeForm(event.content().getId());
                    break;
            }
        }
    }

    private void updateForm(String formId, FormEvent formEvent) {
        final String eventType = formEvent.toString().toLowerCase();
        logger.info("Received {} form event for {}", eventType, formId);
        formService.findById(formId)
                .subscribe(
                        form -> {
                            // check if form has been disabled
                            if (forms.containsKey(formId) && !form.isEnabled()) {
                                removeForm(formId);
                            } else {
                                updateForm(form);
                            }
                            logger.info("Form {} {}d", formId, eventType);
                        },
                        error -> logger.error("Unable to {} form {}", eventType, formId, error),
                        () -> logger.error("No form found with id {}", formId));
    }

    private void removeForm(String formId) {
        logger.info("Received form event, delete form {}", formId);
        Form deletedForm = forms.remove(formId);
        if (deletedForm != null) {
            templateResolver.removeForm(deletedForm);
        }
    }

    private void updateForm(Form form) {
        if (form.getReferenceType() == ReferenceType.ORGANIZATION && form.isEnabled()) {
            this.forms.put(form.getId(), form);
            templateResolver.addForm(form);
            logger.info("Form {} loaded", form.getId());
        }
    }
}
