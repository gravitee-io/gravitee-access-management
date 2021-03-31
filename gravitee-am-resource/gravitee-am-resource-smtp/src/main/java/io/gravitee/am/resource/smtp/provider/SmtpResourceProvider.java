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

import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.email.EmailSenderProvider;
import io.gravitee.am.resource.api.email.Message;
import io.gravitee.am.resource.smtp.SmtpResourceConfiguration;
import io.reactivex.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static io.gravitee.am.resource.api.email.Message.TEXT_HTML;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SmtpResourceProvider implements EmailSenderProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpResourceProvider.class);

    private final static String MAILAPI_PROPERTIES_PREFIX = "mail.";

    @Autowired
    private SmtpResourceConfiguration configuration;

    private JavaMailSender mailSender;

    @Override
    public ResourceProvider start() throws Exception {
        this.mailSender = createJavaMail();
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
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol()+ ".auth", Boolean.toString(configuration.isAuthentication()));
        properties.setProperty(MAILAPI_PROPERTIES_PREFIX + configuration.getProtocol()+ ".starttls.enable", Boolean.toString(configuration.isStartTls()));
        javaMailSender.setJavaMailProperties(properties);
        return javaMailSender;
    }

    @Override
    public Completable sendMessage(Message message) {
        LOGGER.debug("Preparing message...");

        return Completable.create(emitter -> {
            try {
                final MimeMessageHelper email = new MimeMessageHelper(mailSender.createMimeMessage(), false, StandardCharsets.UTF_8.name());

                email.setFrom(configuration.getFrom());
                email.setTo(message.getRecipient());
                email.setSubject(message.getSubject());
                email.setText(message.getContent(), TEXT_HTML.equals(message.getContentType()));

                this.mailSender.send(email.getMimeMessage());

                LOGGER.debug("Message sent!");
                emitter.onComplete();
            } catch (Exception e) {
                LOGGER.error("Message emission fails", e);
                emitter.onError(e);
            }
        });
    }
}
