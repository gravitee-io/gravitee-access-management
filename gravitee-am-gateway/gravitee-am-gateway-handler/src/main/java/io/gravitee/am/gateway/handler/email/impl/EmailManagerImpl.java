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
package io.gravitee.am.gateway.handler.email.impl;

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import io.gravitee.am.gateway.core.event.EmailEvent;
import io.gravitee.am.gateway.handler.email.EmailManager;
import io.gravitee.am.gateway.handler.vertx.view.DomainBasedEmailTemplateLoader;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.EmailRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
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
    private TemplateLoader templateLoader;

    @Value("${email.subject:[Gravitee.io] %s}")
    private String subject;

    @Value("${email.from}")
    private String defaultFrom;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing emails for domain {}", domain.getName());
        emailRepository.findByDomain(domain.getId())
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

        logger.info("Register event listener for email events");
        eventManager.subscribeForEvents(this, EmailEvent.class);
    }


    @Override
    public void onEvent(Event<EmailEvent, Payload> event) {
        if (domain.getId().equals(event.content().getDomain())) {
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
        if (emailTemplates.containsKey(template)) {
            return emailTemplates.get(template);
        } else {
            return defaultEmail(template, defaultSubject, defaultExpiresAfter);
        }
    }

    private Email defaultEmail(String template, String defaultSubject, Integer defaultExpiresAfter) {
        Email defaultEmail = new Email();
        defaultEmail.setTemplate(template);
        defaultEmail.setFrom(defaultFrom);
        defaultEmail.setSubject(format(subject, defaultSubject));
        defaultEmail.setExpiresAfter(defaultExpiresAfter);

        return defaultEmail;
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
        emailTemplates.remove(deletedEmail.getTemplate());
        ((DomainBasedEmailTemplateLoader) templateLoader).removeTemplate(deletedEmail.getTemplate() + TEMPLATE_SUFFIX);
    }

    private void updateEmails(List<Email> emails) {
        emails
                .stream()
                .filter(Email::isEnabled)
                .forEach(email -> {
                    this.emails.put(email.getId(), email);
                    this.emailTemplates.put(email.getTemplate(), email);
                    reloadTemplate(email.getTemplate() + TEMPLATE_SUFFIX, email.getContent());
                    logger.info("Email {} loaded for domain {}", email.getTemplate(), domain.getName());
                });
    }

    private void reloadTemplate(String templateName, String content) {
        ((DomainBasedEmailTemplateLoader) templateLoader).putTemplate(templateName, content, System.currentTimeMillis());
        configuration.clearTemplateCache();
    }
}
