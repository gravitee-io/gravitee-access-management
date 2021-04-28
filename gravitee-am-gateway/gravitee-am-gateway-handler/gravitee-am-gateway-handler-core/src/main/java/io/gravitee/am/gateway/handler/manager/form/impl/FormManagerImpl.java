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
package io.gravitee.am.gateway.handler.manager.form.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.FormEvent;
import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.gateway.handler.vertx.view.thymeleaf.DomainBasedTemplateResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.FormRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FormManagerImpl extends AbstractService implements FormManager, InitializingBean, EventListener<FormEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(FormManagerImpl.class);
    private ConcurrentMap<String, Form> forms = new ConcurrentHashMap<>();

    @Autowired
    private ITemplateResolver templateResolver;

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing forms for domain {}", domain.getName());
        formRepository.findByDomain(domain.getId())
                .subscribe(
                        forms -> {
                            updateForms(forms);
                            logger.info("Forms loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize forms for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for form events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, FormEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for form events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, FormEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<FormEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
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
        logger.info("Domain {} has received {} form event for {}", domain.getName(), eventType, formId);
        formRepository.findById(formId)
                .subscribe(
                        form -> {
                            // check if form has been disabled
                            if (forms.containsKey(formId) && !form.isEnabled()) {
                                removeForm(formId);
                            } else {
                                updateForms(Collections.singletonList(form));
                            }
                            logger.info("Form {} {}d for domain {}", formId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} form for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No form found with id {}", formId));
    }

    private void removeForm(String formId) {
        logger.info("Domain {} has received form event, delete form {}", domain.getName(), formId);
        Form deletedForm = forms.remove(formId);
        if (deletedForm != null) {
            ((DomainBasedTemplateResolver) templateResolver).removeForm(getTemplateName(deletedForm));
        }
    }

    private void updateForms(List<Form> forms) {
        forms
                .stream()
                .filter(Form::isEnabled)
                .forEach(form -> {
                    this.forms.put(form.getId(), form);
                    ((DomainBasedTemplateResolver) templateResolver).addForm(getTemplateName(form), form.getContent());
                    logger.info("Form {} loaded for domain {} " + (form.getClient() != null ? "and client {}" : ""), form.getTemplate(), domain.getName(), form.getClient());
                });
    }

    private String getTemplateName(Form form) {
        return form.getTemplate()
                + ((form.getClient() != null) ? TEMPLATE_NAME_SEPARATOR + form.getClient() : "");
    }
}
