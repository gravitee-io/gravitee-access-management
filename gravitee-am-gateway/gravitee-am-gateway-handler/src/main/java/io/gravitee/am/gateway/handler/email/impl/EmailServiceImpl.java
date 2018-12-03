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

import freemarker.template.Configuration;
import freemarker.template.Template;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.gateway.handler.email.EmailService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailServiceImpl implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Value("${templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    @Value("${email.subject:[Gravitee.io] %s}")
    private String subject;

    @Value("${email.enabled:false}")
    private boolean enabled;

    @Value("${email.from}")
    private String defaultFrom;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private Configuration freemarkerConfiguration;

    @Override
    public void send(Email email) {
        if (enabled) {
            try {
                final MimeMessageHelper mailMessage = new MimeMessageHelper(mailSender.createMimeMessage(), true, StandardCharsets.UTF_8.name());

                final Template template = freemarkerConfiguration.getTemplate(email.getTemplate());
                final String content = processTemplateIntoString(template, email.getParams());

                final String from = isNull(email.getFrom()) || email.getFrom().isEmpty() ? defaultFrom : email.getFrom();

                String fromName = email.getFromName();
                if (fromName == null || fromName.isEmpty()) {
                    mailMessage.setFrom(from);
                } else {
                    mailMessage.setFrom(from, email.getFromName());
                }

                mailMessage.setTo(email.getTo());
                mailMessage.setSubject(format(subject, email.getSubject()));

                final String html = addResourcesInMessage(mailMessage, content);

                LOGGER.debug("Sending an email to: {}\nSubject: {}\nMessage: {}", email.getTo(), email.getSubject(), html);

                mailSender.send(mailMessage.getMimeMessage());
            } catch (final Exception ex) {
                LOGGER.error("Error while sending email", ex);
                new TechnicalManagementException("Error while sending email", ex);
            }
        }
    }

    private String addResourcesInMessage(final MimeMessageHelper mailMessage, final String htmlText) throws Exception {
        final Document document = Jsoup.parse(htmlText);

        final List<String> resources = new ArrayList<>();

        final Elements imageElements = document.getElementsByTag("img");
        resources.addAll(imageElements.stream()
                .filter(imageElement -> imageElement.hasAttr("src"))
                .map(imageElement -> {
                    final String src = imageElement.attr("src");
                    imageElement.attr("src", "cid:" + src);
                    return src;
                })
                .collect(Collectors.toList()));

        final String html = document.html();
        mailMessage.setText(html, true);

        for (final String res : resources) {
            final FileSystemResource templateResource = new FileSystemResource(new File(templatesPath, res));
            mailMessage.addInline(res, templateResource, getContentTypeByFileName(res));
        }

        return html;
    }

    private String getContentTypeByFileName(final String fileName) {
        if (fileName == null) {
            return "";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileName);
    }
}
