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

import freemarker.template.*;
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
import io.netty.handler.codec.http.QueryStringDecoder;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementEmailService")
public class EmailServiceImpl implements EmailService {

    private static final String ADMIN_CLIENT = "admin";

    private final boolean enabled;
    private final String registrationSubject;
    private final Integer registrationExpireAfter;
    private final String registrationVerifySubject;
    private final Integer registrationVerifyExpireAfter;
    private final String certificateExpirySubject;

    private EmailManager emailManager;

    private io.gravitee.am.service.EmailService emailService;

    private Configuration freemarkerConfiguration;

    private AuditService auditService;

    private JWTBuilder jwtBuilder;

    private DomainService domainService;

    public EmailServiceImpl(
            EmailManager emailManager,
            io.gravitee.am.service.EmailService emailService,
            Configuration freemarkerConfiguration,
            AuditService auditService,
            @Qualifier("managementJwtBuilder") JWTBuilder jwtBuilder,
            DomainService domainService,
            @Value("${email.enabled:false}") boolean enabled,
            @Value("${user.registration.email.subject:New user registration}") String registrationSubject,
            @Value("${user.registration.token.expire-after:86400}")  Integer registrationExpireAfter,
            @Value("${user.registration.verify.email.subject:New user registration}") String registrationVerifySubject,
            @Value("${user.registration.verify.token.expire-after:604800}")  Integer registrationVerifyExpireAfter,
            @Value("${services.certificate.expiryEmailSubject:Certificate will expire soon}") String certificateExpirySubject) {
        this.emailManager = emailManager;
        this.emailService = emailService;
        this.freemarkerConfiguration = freemarkerConfiguration;
        this.auditService = auditService;
        this.jwtBuilder = jwtBuilder;
        this.domainService = domainService;
        this.enabled = enabled;
        this.registrationSubject = registrationSubject;
        this.registrationExpireAfter = registrationExpireAfter;
        this.registrationVerifySubject = registrationVerifySubject;
        this.registrationVerifyExpireAfter = registrationVerifyExpireAfter;
        this.certificateExpirySubject = certificateExpirySubject;
    }

    @Override
    public Maybe<Email> send(Domain domain, Application client, io.gravitee.am.model.Template template, User user) {
        if (enabled) {
            // get raw email template
            return getEmailTemplate(template, user).map(emailTemplate -> {
                // prepare email
                Email email = prepareEmail(domain, client, template, emailTemplate, user);
                // send email
                sendEmail(email, user);

                return email;
            });
        }
        return Maybe.empty();
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
        var queryParam = MultiMap.caseInsensitiveMultiMap();
        queryParam.add("token", token);

        if (client != null) {
            queryParam.add("client_id", encodeURIComponent(client.getSettings().getOauth().getClientId()));
        }

        Map<String, Object> params = new HashMap<>();

        if (StringUtils.isNoneBlank(user.getRegistrationUserUri())) {
            queryParam.addAll(getQueryMap(user.getRegistrationUserUri()));
        }

        params.put("url", domainService.buildUrl(domain, redirectUri, queryParam));

        params.put("user", new UserProperties(user));
        params.put("token", token);
        params.put("expireAfterSeconds", expiresAfter);
        params.put("domain", new DomainProperties(domain));
        if (client != null) {
            params.put("client", new ClientProperties(client));
        }

        return params;
    }

    public static MultiMap getQueryMap(String query) {

        var queryParams = MultiMap.caseInsensitiveMultiMap();

        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(query, true);
        queryStringDecoder.parameters().forEach(queryParams::add);

        return queryParams;
    }

    private String getDefaultSubject(io.gravitee.am.model.Template template) {
        switch (template) {
            case REGISTRATION_CONFIRMATION:
                return registrationSubject;
            case REGISTRATION_VERIFY:
                return registrationVerifySubject;
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
            case REGISTRATION_VERIFY:
                return registrationVerifyExpireAfter;
            case CERTIFICATE_EXPIRATION:
                return -1;
            default:
                throw new IllegalArgumentException(template.template() + " not found");
        }
    }
}
