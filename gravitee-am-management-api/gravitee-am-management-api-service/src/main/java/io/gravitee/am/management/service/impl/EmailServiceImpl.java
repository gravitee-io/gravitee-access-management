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

import freemarker.template.Configuration;
import freemarker.template.Template;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.EmailAuditBuilder;
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
import org.springframework.stereotype.Component;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailServiceImpl implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String ADMIN_DOMAIN = "admin";
    private static final String ADMIN_CLIENT = "admin";

    @Value("${templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    @Value("${email.enabled:false}")
    private boolean enabled;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private Configuration freemarkerConfiguration;

    @Autowired
    private AuditService auditService;

    @Override
    public void send(Email email, User user) {
        if (enabled) {
            try {
                final MimeMessageHelper mailMessage = new MimeMessageHelper(mailSender.createMimeMessage(), true, StandardCharsets.UTF_8.name());
                final Template template = freemarkerConfiguration.getTemplate(email.getTemplate());
                final Template plainTextTemplate = new Template("subject", new StringReader(email.getSubject()), freemarkerConfiguration);

                // compute email subject
                final String subject = processTemplateIntoString(plainTextTemplate, email.getParams());
                // compute email content
                final String content = processTemplateIntoString(template, email.getParams());
                final String from = email.getFrom();

                String fromName = email.getFromName();
                if (fromName == null || fromName.isEmpty()) {
                    mailMessage.setFrom(from);
                } else {
                    mailMessage.setFrom(from, fromName);
                }

                mailMessage.setTo(email.getTo());
                mailMessage.setSubject(subject);

                final String html = addResourcesInMessage(mailMessage, content);

                LOGGER.debug("Sending an email to: {}\nSubject: {}\nMessage: {}", email.getTo(), email.getSubject(), html);
                mailSender.send(mailMessage.getMimeMessage());
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class).domain(ADMIN_DOMAIN).client(ADMIN_CLIENT).email(email).user(user));
            } catch (final Exception ex) {
                LOGGER.error("Error while sending email", ex);
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class).domain(ADMIN_DOMAIN).client(ADMIN_CLIENT).email(email).user(user).throwable(ex));
                throw new TechnicalManagementException("Error while sending email", ex);
            }
        }
    }

    private String addResourcesInMessage(final MimeMessageHelper mailMessage, final String htmlText) throws Exception {
        final Document document = Jsoup.parse(htmlText);

        final List<String> resources = new ArrayList<>();

        final Elements imageElements = document.getElementsByTag("img");
        resources.addAll(imageElements.stream()
                .filter(imageElement -> imageElement.hasAttr("src"))
                .filter(imageElement -> !imageElement.attr("src").startsWith("http"))
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
