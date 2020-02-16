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
package io.gravitee.am.gateway.handler.common.email.impl;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import io.gravitee.am.common.event.EmailEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.gateway.handler.common.email.EmailManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.EmailRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailManagerImpl extends AbstractService implements EmailManager, InitializingBean, EventListener<EmailEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(EmailManagerImpl.class);
    private static final String TEMPLATE_SUFFIX = ".html";
    private ConcurrentMap<String, Email> emails = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Email> emailTemplates = new ConcurrentHashMap<>();

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private Configuration configuration;

    @Autowired
    private StringTemplateLoader templateLoader;

    @Value("${email.subject:[Gravitee.io] %s}")
    private String subject;

    @Value("${email.from}")
    private String defaultFrom;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing emails for domain {}", domain.getName());
        emailRepository.findAll(ReferenceType.DOMAIN, domain.getId())
                .subscribe(
                        emails -> {
                            updateEmails(emails);
                            logger.info("Emails loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize emails for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for email events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, EmailEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for email events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, EmailEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<EmailEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateEmail(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeEmail(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Email getEmail(String template, String defaultSubject, int defaultExpiresAfter) {
        boolean templateFound = emailTemplates.containsKey(template);
        String[] templateParts = template.split(Pattern.quote(TEMPLATE_NAME_SEPARATOR));

        // template not found for the client, try at domain level
        if (!templateFound && templateParts.length == 2) {
            template = templateParts[0];
            templateFound = emailTemplates.containsKey(template);
        }

        if (templateFound) {
            Email customEmail = emailTemplates.get(template);
            return create(template, customEmail.getFrom(), customEmail.getFromName(), customEmail.getSubject(), customEmail.getExpiresAfter());
        } else {
            return create(template, defaultFrom, null, format(subject, defaultSubject), defaultExpiresAfter);
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

    private void updateEmail(String emailId, EmailEvent emailEvent) {
        final String eventType = emailEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} email event for {}", domain.getName(), eventType, emailId);
        emailRepository.findById(emailId)
                .subscribe(
                        email -> {
                            // check if email has been disabled
                            if (emails.containsKey(emailId) && !email.isEnabled()) {
                                removeEmail(emailId);
                            } else {
                                updateEmails(Collections.singletonList(email));
                            }
                            logger.info("Email {} {}d for domain {}", emailId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} email for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No email found with id {}", emailId));
    }

    private void removeEmail(String emailId) {
        logger.info("Domain {} has received email event, delete email {}", domain.getName(), emailId);
        Email deletedEmail = emails.remove(emailId);
        if (deletedEmail != null) {
            emailTemplates.remove(getTemplateName(deletedEmail));
            templateLoader.removeTemplate(getTemplateName(deletedEmail) + TEMPLATE_SUFFIX);
        }
    }

    private void updateEmails(List<Email> emails) {
        emails
                .stream()
                .filter(Email::isEnabled)
                .forEach(email -> {
                    String templateName = getTemplateName(email);
                    this.emails.put(email.getId(), email);
                    this.emailTemplates.put(templateName, email);
                    reloadTemplate(templateName + TEMPLATE_SUFFIX, email.getContent());
                    logger.info("Email {} loaded for domain {}", templateName, domain.getName());
                });
    }

    private void reloadTemplate(String templateName, String content) {
        templateLoader.putTemplate(templateName, content, System.currentTimeMillis());
        configuration.clearTemplateCache();
    }

    private String getTemplateName(Email email) {
        return email.getTemplate()
                + ((email.getClient() != null) ? TEMPLATE_NAME_SEPARATOR + email.getClient() : "");
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setDefaultFrom(String defaultFrom) {
        this.defaultFrom = defaultFrom;
    }

    public void setEmailTemplates(ConcurrentMap<String, Email> emailTemplates) {
        this.emailTemplates = emailTemplates;
    }
}
