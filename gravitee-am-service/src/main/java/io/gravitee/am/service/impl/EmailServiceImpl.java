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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.service.EmailService;
import io.gravitee.am.service.i18n.CompositeDictionaryProvider;
import io.gravitee.am.service.i18n.DictionaryProvider;
import io.gravitee.am.service.i18n.FileSystemDictionaryProvider;
import io.gravitee.am.service.utils.EmailSender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailServiceImpl implements EmailService, InitializingBean {

    private String templatesPath;

    private final JavaMailSender mailSender;

    private EmailSender emailSender;

    private DictionaryProvider defaultDictionaryProvider;
    private DictionaryProvider dictionaryProvider;

    public EmailServiceImpl(@Lazy JavaMailSender mailSender, @Value("${templates.path:${gravitee.home}/templates}") String templatesPath) {
        this.mailSender = mailSender;
        this.templatesPath = templatesPath;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.emailSender = new EmailSender(mailSender, templatesPath);
        this.defaultDictionaryProvider = FileSystemDictionaryProvider.getInstance(Paths.get(templatesPath, "i18n").toFile().getAbsolutePath());
    }

    @Override
    public void send(Email email) {
        this.emailSender.send(email);
    }

    @Override
    public void batch(List<Email> emails) {
        this.emailSender.batch(emails);
    }

    @Override
    public DictionaryProvider getDefaultDictionaryProvider() {
        return this.defaultDictionaryProvider;
    }
}
