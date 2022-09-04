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
package io.gravitee.am.resource.smtp.provider;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.email.EmailSenderProvider;
import io.gravitee.am.resource.smtp.SmtpResourceConfiguration;
import io.gravitee.am.service.utils.EmailSender;
import io.reactivex.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SmtpResourceProvider implements EmailSenderProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpResourceProvider.class);

    private final static String MAILAPI_PROPERTIES_PREFIX = "mail.";

    @Autowired
    private SmtpResourceConfiguration configuration;

    @Autowired
    private Environment env;

    private EmailSender mailSender;

    private ExecutorService executorService;

    @Override
    public ResourceProvider start() throws Exception {
        String templatePath = env.getProperty("templates.path");
        if (StringUtils.isEmpty(templatePath)) {
            templatePath = env.getProperty("gravitee.home") + "/templates";
        }
        this.mailSender = new EmailSender(createJavaMail(), templatePath);
        this.executorService = Executors.newCachedThreadPool();
        return this;
    }

    @Override
    public ResourceProvider stop() throws Exception {
        if (executorService != null) {
            executorService.shutdown();
        }
        return this;
    }

    private JavaMailSender createJavaMail() {
        final JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(configuration.getHost());
        try {
            javaMailSender.setPort(configuration.getPort());
        } catch (Exception e) {
        }
        javaMailSender.setUsername(configuration.getUsername());
        javaMailSender.setPassword(configuration.getPassword());
        javaMailSender.setProtocol(configuration.getProtocol());
        Properties properties = new Properties();
        if (configuration.getSslTrust() != null) {
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".ssl.trust", configuration.getSslTrust());
        }
        if (!StringUtils.isEmpty(configuration.getSslProtocols())) {
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol()+ ".ssl.protocols", configuration.getSslProtocols());
        }
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol()+ ".auth", Boolean.toString(configuration.isAuthentication()));
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol()+ ".starttls.enable", Boolean.toString(configuration.isStartTls()));
        javaMailSender.setJavaMailProperties(properties);
        return javaMailSender;
    }

    @Override
    public Completable sendMessage(Email message, boolean overrideFrom) {
        executorService.execute(() -> {
            try {
                if (overrideFrom) {
                    message.setFrom(this.configuration.getFrom());
                }
                this.mailSender.send(message);
            } catch (Exception e) {
                LOGGER.error("Message emission fails", e);
            }
        });
        return Completable.complete();
    }
}
