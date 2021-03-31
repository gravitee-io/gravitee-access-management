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
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.utils.EmailSender;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailServiceImpl implements EmailService, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Value("${templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    @Autowired
    private JavaMailSender mailSender;

    private EmailSender emailSender;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.emailSender = new EmailSender(mailSender, templatesPath);
    }

    @Override
    public void send(Email email) {
        this.emailSender.send(email);
    }
}
