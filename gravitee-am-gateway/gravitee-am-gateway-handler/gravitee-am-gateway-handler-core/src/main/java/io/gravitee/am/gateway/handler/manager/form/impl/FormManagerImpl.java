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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.CustomLog;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class FormManagerImpl extends AbstractService implements FormManager, InitializingBean, EventListener<FormEvent, Payload> {

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
        log.info("Initializing forms for domain {}", domain.getName());
        formRepository.findAll(ReferenceType.DOMAIN, domain.getId())
                .subscribe(
                        form -> updateForm(form),
                        error -> log.error("Unable to initialize forms for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        log.info("Register event listener for form events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, FormEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        log.info("Dispose event listener for form events for domain {}", domain.getName());
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
        log.info("Domain {} has received {} form event for {}", domain.getName(), eventType, formId);
        formRepository.findById(formId)
                .subscribe(
                        form -> {
                            if (needDeployment(form)) {
                                // check if form has been disabled
                                if (forms.containsKey(formId) && !form.isEnabled()) {
                                    removeForm(formId);
                                } else {
                                    updateForm(form);
                                }
                                log.info("Form {} {}d for domain {}", formId, eventType, domain.getName());
                            } else {
                                log.info("Form {} already {}d for domain {}", formId, eventType, domain.getName());
                            }
                        },
                        error -> log.error("Unable to {} form for domain {}", eventType, domain.getName(), error),
                        () -> log.error("No form found with id {}", formId));
    }

    private void removeForm(String formId) {
        log.info("Domain {} has received form event, delete form {}", domain.getName(), formId);
        Form deletedForm = forms.remove(formId);
        if (deletedForm != null) {
            ((DomainBasedTemplateResolver) templateResolver).removeForm(getTemplateName(deletedForm));
        }
    }

    private void updateForm(Form form) {
        if (form.isEnabled()){
            this.forms.put(form.getId(), form);
            ((DomainBasedTemplateResolver) templateResolver).addForm(getTemplateName(form), form.getContent());
            log.info("Form {} loaded for domain {} " + (form.getClient() != null ? "and client {}" : ""), form.getTemplate(), domain.getName(), form.getClient());
        }
    }

    private String getTemplateName(Form form) {
        return form.getTemplate()
                + ((form.getClient() != null) ? TEMPLATE_NAME_SEPARATOR + form.getClient() : "");
    }

    /**
     * @param form
     * @return true if the Form has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(Form form) {
        final Form deployedForm = this.forms.get(form.getId());
        return (deployedForm == null || deployedForm.getUpdatedAt().before(form.getUpdatedAt()));
    }
}
