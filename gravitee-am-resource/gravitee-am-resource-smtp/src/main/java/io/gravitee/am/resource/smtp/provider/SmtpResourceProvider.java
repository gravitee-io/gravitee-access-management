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
import io.gravitee.am.resource.smtp.configuration.SmtpResourceConfiguration;
import io.gravitee.am.service.spring.email.OAuth2JavaMailSenderWrapper;
import io.gravitee.am.service.spring.email.OAuth2TokenService;
import io.gravitee.am.service.utils.EmailSender;
import io.reactivex.rxjava3.core.Completable;
import org.eclipse.angus.mail.auth.OAuth2SaslClientFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(SmtpResourceProvider.class);
    private static final String MAILAPI_PROPERTIES_PREFIX = "mail.";

    @Autowired
    private SmtpResourceConfiguration configuration;

    @Autowired
    private Environment env;

    @Autowired
    private OAuth2TokenService oauth2TokenService;

    private EmailSender mailSender;

    private ExecutorService executorService;

    @Override
    public ResourceProvider start() throws Exception {
        String templatePath = env.getProperty("templates.path");
        if (!StringUtils.hasText(templatePath)) {
            templatePath = env.getProperty("gravitee.home") + "/templates";
        }

        JavaMailSender javaMailSender;
        if (configuration.isOauth2Authentication()) {
            logger.debug("OAuth2 authentication enabled for SMTP resource");
            oauth2TokenService.initialize(
                    configuration.getOauth2Configuration().getTokenEndpoint(),
                    configuration.getOauth2Configuration().getOauth2ClientId(),
                    configuration.getOauth2Configuration().getOauth2ClientSecret(),
                    configuration.getOauth2Configuration().getOauth2RefreshToken(),
                    configuration.getOauth2Configuration().getOauth2Scope()
            );
            JavaMailSenderImpl javaMailSenderImpl = createJavaMailForOAuth2();
            javaMailSender = new OAuth2JavaMailSenderWrapper(javaMailSenderImpl, oauth2TokenService);
        } else if (configuration.isBasicAuthentication()) {
            logger.debug("Basic authentication enabled for SMTP resource");
            javaMailSender = createJavaMailForBasic();
        } else {
            logger.debug("No authentication for SMTP resource");
            javaMailSender = createJavaMailWithoutAuth();
        }

        this.mailSender = new EmailSender(javaMailSender, templatePath);
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

    private JavaMailSenderImpl createJavaMailForOAuth2() {
        final JavaMailSenderImpl javaMailSender = createBaseJavaMailSender();

        Properties properties = createBaseProperties();
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".auth.mechanisms", "XOAUTH2");
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".auth.plain.disable", "true");
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".auth.login.disable", "true");
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".auth", "true");

        javaMailSender.setJavaMailProperties(properties);
        OAuth2SaslClientFactory.init();
        javaMailSender.setUsername(configuration.getOauth2Configuration().getOauth2Username());

        return javaMailSender;
    }

    private JavaMailSenderImpl createJavaMailForBasic() {
        final JavaMailSenderImpl javaMailSender = createBaseJavaMailSender();

        javaMailSender.setUsername(configuration.getUsername());
        javaMailSender.setPassword(configuration.getPassword());

        Properties properties = createBaseProperties();
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".auth", "true");

        javaMailSender.setJavaMailProperties(properties);
        return javaMailSender;
    }

    private JavaMailSenderImpl createJavaMailWithoutAuth() {
        final JavaMailSenderImpl javaMailSender = createBaseJavaMailSender();

        Properties properties = createBaseProperties();
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".auth", "false");

        javaMailSender.setJavaMailProperties(properties);
        return javaMailSender;
    }

    private JavaMailSenderImpl createBaseJavaMailSender() {
        final JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(configuration.getHost());
        try {
            javaMailSender.setPort(configuration.getPort());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot configure JavaMail Sender port", e);
        }
        javaMailSender.setProtocol(configuration.getProtocol());
        return javaMailSender;
    }

    private Properties createBaseProperties() {
        Properties properties = new Properties();
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".starttls.enable", Boolean.toString(configuration.isStartTls()));

        if (configuration.getSslTrust() != null) {
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".ssl.trust", configuration.getSslTrust());
        }
        if (StringUtils.hasText(configuration.getSslProtocols())) {
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol() + ".ssl.protocols", configuration.getSslProtocols());
        }

        return properties;
    }

    @Override
    public Completable sendMessage(Email message, boolean overrideFrom) {
        return Completable.fromFuture(
                executorService.submit(() -> {
                            if (overrideFrom) {
                                message.setFrom(this.configuration.getFrom());
                            }
                            this.mailSender.send(message);
                        }
                ));
    }
}
