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
import io.gravitee.am.service.i18n.ThreadLocalDomainDictionaryProvider;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.EmailAuditBuilder;
<<<<<<< HEAD
=======
import io.gravitee.am.service.validators.email.EmailDomainValidator;
import io.netty.handler.codec.http.QueryStringDecoder;
>>>>>>> 8c006cf9c1 (feat: email allow list to protect from impersonation)
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static io.gravitee.am.service.utils.UserProfileUtils.preferredLanguage;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementEmailService")
public class EmailServiceImpl implements EmailService, InitializingBean {

    private static final String ADMIN_CLIENT = "admin";

    private final boolean enabled;
    private final String registrationSubject;
    private final Integer registrationExpireAfter;
    private final String certificateExpirySubject;

<<<<<<< HEAD
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

    @Autowired
    private I18nDictionaryService i18nDictionaryService;
=======
    private final EmailManager emailManager;

    private final io.gravitee.am.service.EmailService emailService;

    private final Configuration freemarkerConfiguration;

    private final AuditService auditService;

    private final JWTBuilder jwtBuilder;

    private final DomainService domainService;

    private final I18nDictionaryService i18nDictionaryService;
>>>>>>> 8c006cf9c1 (feat: email allow list to protect from impersonation)

    private final ThreadLocalDomainDictionaryProvider dictionaryProvider;

    public EmailServiceImpl(
            @Value("${email.enabled:false}") boolean enabled,
            @Value("${user.registration.email.subject:New user registration}") String registrationSubject,
            @Value("${user.registration.token.expire-after:86400}")  Integer registrationExpireAfter,
            @Value("${services.certificate.expiryEmailSubject:Certificate will expire soon}") String certificateExpirySubject) {
        this.enabled = enabled;
        this.registrationSubject = registrationSubject;
        this.registrationExpireAfter = registrationExpireAfter;
        this.certificateExpirySubject = certificateExpirySubject;
        this.dictionaryProvider = new ThreadLocalDomainDictionaryProvider();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.emailService.setDictionaryProvider(dictionaryProvider);
    }

    @Override
    public Completable send(Domain domain, Application client, io.gravitee.am.model.Template template, User user) {
        if (enabled) {
            return refreshDomainDictionaries(domain)
                    .andThen(prepareAndSend(domain, client, template, user));
        }
        return Completable.complete();
    }

    private Completable prepareAndSend(Domain domain, Application client, io.gravitee.am.model.Template template, User user) {
        // get raw email template
        return getEmailTemplate(template, user).map(emailTemplate -> {
            // prepare email
            Email email = prepareEmail(domain, client, template, emailTemplate, user);
            // send email
            sendEmail(email, user);
<<<<<<< HEAD

            return email;
        }).ignoreElement();
=======
            return email;
        });
>>>>>>> 8c006cf9c1 (feat: email allow list to protect from impersonation)
    }

    private Completable refreshDomainDictionaries(Domain domain) {
        return Completable.fromAction(() -> this.dictionaryProvider.resetDictionaries())
                .andThen(this.i18nDictionaryService.findAll(ReferenceType.DOMAIN, domain.getId())
                .map(dict -> {
                    this.dictionaryProvider.loadDictionary(dict);
                    return dict;
                }).ignoreElements().onErrorComplete());
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
        dataModel.put(FreemarkerMessageResolver.METHOD_NAME, new FreemarkerMessageResolver(this.emailService.getDictionaryProvider().getDictionaryFor(preferredLanguage)));

        var env = plainTextTemplate.createProcessingEnvironment(new SimpleHash(dataModel,
                new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_22).build()), result);
        env.process();

        return result.toString();
    }

    private Email prepareEmail(Domain domain, Application client, io.gravitee.am.model.Template template, io.gravitee.am.model.Email emailTemplate, User user) {
        Map<String, Object> params = prepareEmailParams(domain, client, user, emailTemplate.getExpiresAfter(), template.redirectUri());
        return new EmailBuilder()
                .to(user.getEmail())
                .from(emailTemplate.getFrom())
                .fromName(emailTemplate.getFromName())
                .subject(emailTemplate.getSubject())
                .template(emailTemplate.getTemplate())
                .params(params)
                .build();
    }

    private Map<String, Object> prepareEmailParams(Domain domain, Application client, User user, Integer expiresAfter, String redirectUri) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        Instant now = Instant.now();
        claims.put(Claims.iat, now.getEpochSecond());
        claims.put(Claims.exp, now.plusSeconds(expiresAfter).getEpochSecond());
        claims.put(Claims.sub, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.aud, user.getClient());
        }

        String token = jwtBuilder.sign(new JWT(claims));
        String redirectUrl = domainService.buildUrl(domain, redirectUri + "?token=" + token);

        if (client != null) {
            redirectUrl += "&client_id=" + encodeURIComponent(client.getSettings().getOauth().getClientId());
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
