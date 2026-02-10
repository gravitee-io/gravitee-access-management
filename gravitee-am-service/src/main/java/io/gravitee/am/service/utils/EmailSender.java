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
package io.gravitee.am.service.utils;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.service.exception.BatchEmailException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class created to avoid duplication between {@link io.gravitee.am.service.impl.EmailServiceImpl} and the SmtpResourceProvider
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;

    private final String templatesPath;

    public EmailSender(JavaMailSender mailSender, String templatesPath) {
        this.mailSender = mailSender;
        this.templatesPath = templatesPath;
    }

    public void send(Email email) {
        try {
            final MimeMessageHelper mailMessage = prepareMimeMessage(email);
            mailSender.send(mailMessage.getMimeMessage());
        } catch (final Exception ex) {
            LOGGER.error("Error while creating email", ex);
            throw new TechnicalManagementException("Error while creating email", ex);
        }
    }

    public void batch(List<Email> emails) {
        if (emails.isEmpty()) {
            return;
        }

        try {
            var messages = new MimeMessage[emails.size()];
            for (int i = 0; i < emails.size(); i++) {
                final MimeMessageHelper mailMessage = prepareMimeMessage(emails.get(i));
                messages[i] = mailMessage.getMimeMessage();
            }

            mailSender.send(messages);
        } catch (MailSendException ex) {
            if (ex.getFailedMessages() != null && !ex.getFailedMessages().isEmpty()) {
                var addresses = ex.getFailedMessages().keySet().stream().map(mimeMsg -> {
                    try {
                        return ((MimeMessage)mimeMsg).getRecipients(Message.RecipientType.TO)[0];
                    } catch (MessagingException e) {
                        LOGGER.warn("Unable to extract emailAddress from the exception, ignore it in the batch audits", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(inetAdd -> ((InternetAddress)inetAdd).getAddress()).toList();
                throw new BatchEmailException("Error while creating emails", addresses);
            } else {
                throw new BatchEmailException("Error while creating emails", extractEmailAddress(emails));
            }
        } catch (final Exception ex) {
            LOGGER.error("Error while creating email", ex);
            throw new BatchEmailException("Error while creating email", extractEmailAddress(emails));
        }
    }

    private static List<String> extractEmailAddress(List<Email> emails) {
        return emails.stream().map(Email::getTo).filter(to -> to.length > 0).map(to -> to[0]).toList();
    }

    private MimeMessageHelper prepareMimeMessage(Email email) throws Exception {
        final MimeMessageHelper mailMessage = new MimeMessageHelper(mailSender.createMimeMessage(), true, StandardCharsets.UTF_8.name());
        final String subject = email.getSubject();
        final String content = email.getContent();
        final String from = email.getFrom();
        final String[] to = email.getTo();

        String fromName = email.getFromName();
        if (fromName == null || fromName.isEmpty()) {
            mailMessage.setFrom(from);
        } else {
            mailMessage.setFrom(from, fromName);
        }

        mailMessage.setTo(to);
        mailMessage.setSubject(subject);

        final String html = addResourcesInMessage(mailMessage, content);
        LOGGER.debug("Sending an email to: {}\nSubject: {}\nMessage: {}", email.getTo(), email.getSubject(), html);
        return mailMessage;
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
            if (res.startsWith("data:image/")) {
                final String value = res.replaceFirst("^data:image/[^;]*;base64,?", "");
                byte[] bytes = Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
                mailMessage.addInline(res, new ByteArrayResource(bytes), extractMimeType(res));
            } else {
                File file = new File(templatesPath, res);
                if (file.getCanonicalPath().startsWith(templatesPath)) {
                    final FileSystemResource templateResource = new FileSystemResource(file);
                    mailMessage.addInline(res, templateResource, getContentTypeByFileName(res));
                } else {
                    LOGGER.warn("Resource path invalid : {}", file.getPath());
                }
            }
        }

        return html;
    }

    private String getContentTypeByFileName(final String fileName) {
        if (fileName == null) {
            return "";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileName);
    }

    /**
     * Extract the MIME type from a base64 string
     * @param encoded Base64 string
     * @return MIME type string
     */
    private static String extractMimeType(final String encoded) {
        final Pattern mime = Pattern.compile("^data:([a-zA-Z0-9]+/[a-zA-Z0-9]+).*,.*");
        final Matcher matcher = mime.matcher(encoded);
        return matcher.find() ? matcher.group(1).toLowerCase() : "";
    }
}
