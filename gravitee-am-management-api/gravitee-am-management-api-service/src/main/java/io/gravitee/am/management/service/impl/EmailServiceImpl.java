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
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.SimpleHash;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.common.email.EmailBuilder;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.i18n.FreemarkerMessageResolver;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.EmailAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.gravitee.am.service.utils.UserProfileUtils.preferredLanguage;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementEmailService")
public class EmailServiceImpl implements EmailService {

    private static final String ADMIN_CLIENT = "admin";

    @Value("${email.enabled:false}")
    private boolean enabled;

    @Value("${user.registration.email.subject:${msg('registration_confirmation.email.subject')}}")
    private String registrationSubject;

    @Value("${user.registration.token.expire-after:86400}")
    private Integer registrationExpireAfter;

    @Value("${services.certificate.expiryEmailSubject:Certificate will expire soon}")
    private String certificateExpirySubject;

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
    public Completable send(Domain domain, Application client, io.gravitee.am.model.Template template, User user) {
        if (enabled) {
            // get raw email template
            return getEmailTemplate(template, user).map(emailTemplate -> {
                // prepare email
                Email email = prepareEmail(domain, client, template, emailTemplate, user);
                // send email
                sendEmail(email, user);

                return email;
            }).ignoreElement();
        }
        return Completable.complete();
    }

    @Override
    public Maybe<io.gravitee.am.model.Email> getEmailTemplate(io.gravitee.am.model.Template template, User user) {
        return emailManager.getEmail(template, user, getDefaultSubject(template), getDefaultExpireAt(template));
    }

    private void sendEmail(Email email, User user) {
        if (enabled) {
            try {
                final var locale = preferredLanguage(user, Locale.ENGLISH);
                final var emailToSend = processEmailTemplate(email, locale);
                emailService.send(emailToSend);
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class).domain(user.getReferenceId()).client(ADMIN_CLIENT).email(email).user(user));
            } catch (final Exception ex) {
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class).domain(user.getReferenceId()).client(ADMIN_CLIENT).email(email).throwable(ex));
            }
        }
    }

    @Override
    public Maybe<Email> getFinalEmail(Domain domain, Application client, io.gravitee.am.model.Template template, User user, Map<String, Object> params) {
        // get raw email template
        return emailManager.getEmail(template, ReferenceType.DOMAIN, domain.getId(), user, getDefaultSubject(template), getDefaultExpireAt(template))
                .map(emailTemplate -> {
                    // prepare email
                    final var email = prepareEmail(domain, client, template, emailTemplate, user);

                    if (email.getParams() != null) {
                        email.getParams().putAll(params);
                    } else {
                        email.setParams(params);
                    }

                    // send email
                    var locale = preferredLanguage(user, Locale.ENGLISH);
                    return processEmailTemplate(email, locale);
                });
    }

    private Email processEmailTemplate(Email email, Locale locale) throws IOException, TemplateException {
        final Template template = freemarkerConfiguration.getTemplate(email.getTemplate());
        final Template plainTextTemplate = new Template("subject", new StringReader(email.getSubject()), freemarkerConfiguration);
        // compute email subject
        final String subject = processTemplate(plainTextTemplate, email.getParams(), locale);
        // compute email content
        final String content = processTemplate(template, email.getParams(), locale);
        final Email emailToSend = new Email(email);
        emailToSend.setSubject(subject);
        emailToSend.setContent(content);
        return emailToSend;
    }

    private String processTemplate(Template plainTextTemplate, Map<String, Object> params, Locale preferredLanguage) throws TemplateException, IOException {
        var result = new StringWriter(1024);

        var dataModel = new HashMap<>(params);
        dataModel.put(FreemarkerMessageResolver.METHOD_NAME, new FreemarkerMessageResolver(this.emailService.getDefaultDictionaryProvider().getDictionaryFor(preferredLanguage)));

        var env = plainTextTemplate.createProcessingEnvironment(new SimpleHash(dataModel,
                new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_22).build()), result);
        env.process();

        return result.toString();
    }

    private Email prepareEmail(Domain domain, Application client, io.gravitee.am.model.Template template, io.gravitee.am.model.Email emailTemplate, User user) {
        Map<String, Object> params = prepareEmailParams(domain, client, user, emailTemplate.getExpiresAfter(), template.redirectUri());
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

    private Map<String, Object> prepareEmailParams(Domain domain, Application client, User user, Integer expiresAfter, String redirectUri) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.iat, new Date().getTime() / 1000);
        claims.put(Claims.exp, new Date(System.currentTimeMillis() + (expiresAfter * 1000)).getTime() / 1000);
        claims.put(Claims.sub, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.aud, user.getClient());
        }

        String token = jwtBuilder.sign(new JWT(claims));
        String redirectUrl = domainService.buildUrl(domain, redirectUri + "?token=" + token);

        if (client != null) {
            redirectUrl += "&client_id=" + client.getSettings().getOauth().getClientId();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("user", new UserProperties(user));
        params.put("url", redirectUrl);
        params.put("token", token);
        params.put("expireAfterSeconds", expiresAfter);
        params.put("domain", new DomainProperties(domain));
        if (client != null) {
            params.put("client", new ClientProperties(client));
        }

        return params;
    }

    private String getDefaultSubject(io.gravitee.am.model.Template template) {
        switch (template) {
            case REGISTRATION_CONFIRMATION:
                return registrationSubject;
            case CERTIFICATE_EXPIRATION:
                return certificateExpirySubject;
            default:
                throw new IllegalArgumentException(template.template() + " not found");
        }
    }

    private Integer getDefaultExpireAt(io.gravitee.am.model.Template template) {
        switch (template) {
            case REGISTRATION_CONFIRMATION:
                return registrationExpireAfter;
            case CERTIFICATE_EXPIRATION:
                return -1;
            default:
                throw new IllegalArgumentException(template.template() + " not found");
        }
    }
}
