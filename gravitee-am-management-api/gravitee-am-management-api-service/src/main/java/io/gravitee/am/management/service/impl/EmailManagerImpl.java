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

import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import io.gravitee.am.common.event.EmailEvent;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.lang.String.format;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailManagerImpl extends AbstractService<EmailManager> implements EmailManager, EventListener<EmailEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(EmailManagerImpl.class);
    private static final String TEMPLATE_SUFFIX = ".html";
    private ConcurrentMap<String, Email> emailTemplates = new ConcurrentHashMap<>();

    @Value("${email.from}")
    private String defaultFrom;

    @Value("${email.subject:[Gravitee.io] %s}")
    private String subject;

    @Autowired
    private TemplateLoader templateLoader;

    @Autowired
    private Configuration configuration;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private EventManager eventManager;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for email events for the management API");
        eventManager.subscribeForEvents(this, EmailEvent.class);

        logger.info("Initializing emails");
        emailTemplateService.findAll()
                .filter(Email::isEnabled)
                .blockingIterable()
                .forEach(this::loadEmail);
    }

    @Override
    public void onEvent(Event<EmailEvent, Payload> event) {
        switch (event.type()) {
            case UNDEPLOY:
                removeEmail(event.content().getId());
                break;
            default:
                logger.debug("{} event received for EmailTemplate {}, ignore it as it will be loaded on demand", event.type(), event.content().getId());
        }
    }

    @Override
    public Maybe<Email> getEmail(io.gravitee.am.model.Template templateDef, User user, String defaultSubject, int defaultExpiresAfter) {
        return getEmail0(templateDef, user.getReferenceType(), user.getReferenceId(), user, defaultSubject, defaultExpiresAfter);
    }

    @Override
    public Maybe<Email> getEmail(Template template, ReferenceType referenceType, String referenceId, User user, String defaultSubject, int defaultExpiresAfter) {
        return getEmail0(template, referenceType, referenceId, user, defaultSubject, defaultExpiresAfter);
    }

    private Maybe<Email> getEmail0(Template template, ReferenceType referenceType, String referenceId, User user, String defaultSubject, int defaultExpiresAfter) {
        // Since https://github.com/gravitee-io/issues/issues/6590 we have to read the record in Email repository
        return innerGetEmail(template, referenceType, referenceId, user)
                .map(customEmail -> {
                    // try to found email template in the local map
                    final String templateName = getTemplateName(customEmail);
                    final Email localEmailTemplate = emailTemplates.get(templateName);
                    if (localEmailTemplate != null && localEmailTemplate.getUpdatedAt().getTime() >= customEmail.getUpdatedAt().getTime()) {
                        return create(templateName, localEmailTemplate.getFrom(), localEmailTemplate.getFromName(), localEmailTemplate.getSubject(), localEmailTemplate.getExpiresAfter());
                    }
                    // else, reload the local map and return the database copy one
                    loadEmail(customEmail);
                    return create(templateName, customEmail.getFrom(), customEmail.getFromName(), customEmail.getSubject(), customEmail.getExpiresAfter());

                })
                // if there is nothing in database, return the classpath copy one
                .defaultIfEmpty(create(template.template(), defaultFrom, null, format(subject, defaultSubject), defaultExpiresAfter));
    }

    private void removeEmail(String email) {
        logger.info("Management API has received a undeploy email event for {}", email);
        Optional<Email> emailOptional = emailTemplates.values().stream().filter(email1 -> email.equals(email1.getId())).findFirst();
        if (emailOptional.isPresent()) {
            Email emailToRemove = emailOptional.get();
            emailTemplates.remove(getTemplateName(emailToRemove));
            ((StringTemplateLoader) templateLoader).removeTemplate(getTemplateName(emailToRemove) + TEMPLATE_SUFFIX);
        }
    }

    public void loadEmail(Email email) {
        final String templateName = getTemplateName(email);
        if (email.isEnabled()) {
            reloadTemplate(templateName + TEMPLATE_SUFFIX, email.getContent());
            emailTemplates.put(templateName, email);
        } else {
            // remove email who has been disabled
            emailTemplates.remove(templateName);
            ((StringTemplateLoader) templateLoader).removeTemplate(templateName + TEMPLATE_SUFFIX);
        }
    }

    private Email create(String template, String from, String fromName, String subject, int expiresAt) {
        Email email = new Email();
        email.setTemplate(template);
        email.setFrom(from);
        email.setFromName(fromName);
        email.setSubject(subject);
        email.setExpiresAfter(expiresAt);
        return email;
    }

    private void reloadTemplate(String templateName, String content) {
        ((StringTemplateLoader) templateLoader).putTemplate(templateName, content, System.currentTimeMillis());
        configuration.clearTemplateCache();
    }

    private String getTemplateName(Email email) {
        return email.getTemplate()
                + TEMPLATE_NAME_SEPARATOR
                + email.getReferenceType() + email.getReferenceId()
                + ((email.getClient() != null) ? TEMPLATE_NAME_SEPARATOR + email.getClient() : "");
    }

    private Maybe<Email> innerGetEmail(io.gravitee.am.model.Template templateDef, ReferenceType refType, String referenceId, User user) {
        if (user.getClient() == null) {
            return emailTemplateService.findByTemplate(refType, referenceId, templateDef.template())
                    .filter(Email::isEnabled);
        }
        return emailTemplateService.findByClientAndTemplate(refType, referenceId, user.getClient(), templateDef.template())
                .filter(Email::isEnabled)
                .switchIfEmpty(Maybe.defer(() -> emailTemplateService.findByTemplate(refType, referenceId, templateDef.template())))
                .filter(Email::isEnabled);
    }

    public void setDefaultFrom(String defaultFrom) {
        this.defaultFrom = defaultFrom;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
