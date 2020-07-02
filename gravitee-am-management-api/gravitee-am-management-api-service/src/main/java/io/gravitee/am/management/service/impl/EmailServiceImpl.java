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
import io.gravitee.am.common.email.EmailBuilder;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.PathUtils;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.EmailAuditBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementEmailService")
public class EmailServiceImpl implements EmailService {

    private static final String ADMIN_CLIENT = "admin";

    @Value("${email.enabled:false}")
    private boolean enabled;

    @Value("${user.registration.email.subject:New user registration}")
    private String registrationSubject;

    @Value("${user.registration.token.expire-after:86400}")
    private Integer registrationExpireAfter;

    @Value("${gateway.url:http://localhost:8092}")
    private String gatewayUrl;

    @Autowired
    private EmailManager emailManager;

    @Autowired
    private io.gravitee.am.service.EmailService emailService;

    @Autowired
    private Configuration freemarkerConfiguration;

    @Autowired
    private AuditService auditService;

    @Autowired
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Autowired
    private DomainService domainService;

    @Override
    public void send(Domain domain, io.gravitee.am.model.Template template, User user) {
        if (enabled) {
            // get raw email template
            io.gravitee.am.model.Email emailTemplate = getEmailTemplate(template, user);
            // prepare email
            Email email = prepareEmail(domain, template, emailTemplate, user);
            // send email
            sendEmail(email, user);
        }
    }

    private void sendEmail(Email email, User user) {
        if (enabled) {
            try {
                final Template template = freemarkerConfiguration.getTemplate(email.getTemplate());
                final Template plainTextTemplate = new Template("subject", new StringReader(email.getSubject()), freemarkerConfiguration);
                // compute email subject
                final String subject = processTemplateIntoString(plainTextTemplate, email.getParams());
                // compute email content
                final String content = processTemplateIntoString(template, email.getParams());
                final Email emailToSend = new Email(email);
                emailToSend.setSubject(subject);
                emailToSend.setContent(content);
                emailService.send(emailToSend);
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class).domain(user.getReferenceId()).client(ADMIN_CLIENT).email(email).user(user));
            } catch (final Exception ex) {
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class).domain(user.getReferenceId()).client(ADMIN_CLIENT).email(email).throwable(ex));
            }
        }
    }

    private Email prepareEmail(Domain domain, io.gravitee.am.model.Template template, io.gravitee.am.model.Email emailTemplate, User user) {
        Map<String, Object> params = prepareEmailParams(domain, user, emailTemplate.getExpiresAfter(), template.redirectUri());
        Email email = new EmailBuilder()
                .to(user.getEmail())
                .from(emailTemplate.getFrom())
                .fromName(emailTemplate.getFromName())
                .subject(emailTemplate.getSubject())
                .template(emailTemplate.getTemplate())
                .params(params)
                .build();
        return email;
    }

    private Map<String, Object> prepareEmailParams(Domain domain, User user, Integer expiresAfter, String redirectUri) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.iat, new Date().getTime() / 1000);
        claims.put(Claims.exp, new Date(System.currentTimeMillis() + (expiresAfter * 1000)).getTime() / 1000);
        claims.put(Claims.sub, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.aud, user.getClient());
        }
        claims.put(StandardClaims.EMAIL, user.getEmail());
        claims.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        claims.put(StandardClaims.FAMILY_NAME, user.getLastName());

        String token = jwtBuilder.sign(new JWT(claims));
        String redirectUrl =  domainService.buildUrl(domain, redirectUri + "?token=" + token);

        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        params.put("url", redirectUrl);
        params.put("token", token);
        params.put("expireAfterSeconds", expiresAfter);

        return params;
    }

    private io.gravitee.am.model.Email getEmailTemplate(io.gravitee.am.model.Template template, User user) {
        return emailManager.getEmail(getTemplateName(template, user), getDefaultSubject(template), getDefaultExpireAt(template));
    }

    private String getTemplateName(io.gravitee.am.model.Template template, User user) {
        return template.template()
                + EmailManager.TEMPLATE_NAME_SEPARATOR
                + user.getReferenceType() + user.getReferenceId()
                + ((user.getClient() != null) ? EmailManager.TEMPLATE_NAME_SEPARATOR + user.getClient() : "");
    }

    private String getDefaultSubject(io.gravitee.am.model.Template template) {
        switch (template) {
            case REGISTRATION_CONFIRMATION:
                return registrationSubject;
            default:
                throw new IllegalArgumentException(template.template() + " not found");
        }
    }

    private Integer getDefaultExpireAt(io.gravitee.am.model.Template template) {
        switch (template) {
            case REGISTRATION_CONFIRMATION:
                return registrationExpireAfter;
            default:
                throw new IllegalArgumentException(template.template() + " not found");
        }
    }
}
