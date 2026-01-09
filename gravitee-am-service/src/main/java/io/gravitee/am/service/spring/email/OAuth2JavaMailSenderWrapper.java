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
package io.gravitee.am.service.spring.email;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;

public class OAuth2JavaMailSenderWrapper implements JavaMailSender {

    private final JavaMailSenderImpl wrappedSender;
    private final OAuth2TokenService oauth2TokenService;

    public OAuth2JavaMailSenderWrapper(JavaMailSenderImpl wrappedSender, OAuth2TokenService oauth2TokenService) {
        this.wrappedSender = wrappedSender;
        this.oauth2TokenService = oauth2TokenService;
    }

    private void updateAccessToken() {
        String freshToken = oauth2TokenService.getAccessToken();
        wrappedSender.setPassword(freshToken);
    }

    @Override
    public MimeMessage createMimeMessage() {
        return wrappedSender.createMimeMessage();
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return wrappedSender.createMimeMessage(contentStream);
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        updateAccessToken();
        wrappedSender.send(mimeMessage);
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        updateAccessToken();
        wrappedSender.send(mimeMessages);
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        updateAccessToken();
        wrappedSender.send(mimeMessagePreparator);
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        updateAccessToken();
        wrappedSender.send(mimeMessagePreparators);
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        updateAccessToken();
        wrappedSender.send(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        updateAccessToken();
        wrappedSender.send(simpleMessages);
    }
}
