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
import io.gravitee.am.common.jwt.TokenPurpose;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.i18n.CompositeDictionaryProvider;
import io.gravitee.am.service.i18n.DictionaryProvider;
import io.gravitee.am.service.i18n.DomainBasedDictionaryProvider;
import io.gravitee.am.service.i18n.FreemarkerMessageResolver;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.EmailAuditBuilder;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
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

    private final EmailManager emailManager;

    private final io.gravitee.am.service.EmailService emailService;

    private final Configuration freemarkerConfiguration;

    private final AuditService auditService;

    private final JWTBuilder jwtBuilder;

    private final DomainReadService domainService;

    private final I18nDictionaryService i18nDictionaryService;

    private final Environment environment;

    @Lazy
    public EmailServiceImpl(
            EmailManager emailManager,
            io.gravitee.am.service.EmailService emailService,
            Configuration freemarkerConfiguration,
            AuditService auditService,
            @Lazy @Qualifier("managementJwtBuilder") JWTBuilder jwtBuilder, // Need to be lazy loaded to ensure jwt builder is instantiated with all resolved configuration included eventual secrets.
            DomainReadService domainService,
            I18nDictionaryService i18nDictionaryService,
            Environment environment) {
        this.emailManager = emailManager;
        this.emailService = emailService;
        this.freemarkerConfiguration = freemarkerConfiguration;
        this.auditService = auditService;
        this.jwtBuilder = jwtBuilder;
        this.domainService = domainService;
        this.environment = environment;
        this.enabled = enabled();
        this.registrationSubject = registrationSubject();
        this.registrationExpireAfter = registrationExpireAfter();
        this.registrationVerifySubject = registrationVerifySubject();
        this.registrationVerifyExpireAfter = registrationVerifyExpireAfter();
        this.certificateExpirySubject = certificateExpirySubject();
        this.i18nDictionaryService = i18nDictionaryService;
    }

    @Override
    public Maybe<Email> send(Domain domain, Application client, io.gravitee.am.model.Template template, User user) {
        if (enabled) {
            return refreshDomainDictionaries(domain)
                    .flatMapMaybe(provider -> prepareAndSend(domain, client, template, user, provider));
        }
        return Maybe.empty();
    }

    private Maybe<Email> prepareAndSend(Domain domain, Application client, io.gravitee.am.model.Template template, User user, DomainBasedDictionaryProvider dictionaryProvider) {
        // get raw email template
        return getEmailTemplate(template, user).map(emailTemplate -> {
            // prepare email
            Email email = prepareEmail(domain, client, template, emailTemplate, user);
            // send email
            sendEmail(email, user, dictionaryProvider);
            return email;
        });
    }

    private Single<DomainBasedDictionaryProvider> refreshDomainDictionaries(Domain domain) {
        return this.i18nDictionaryService.findAll(ReferenceType.DOMAIN, domain.getId())
                .collect(DomainBasedDictionaryProvider::new, (prov, dict) -> prov.loadDictionary(dict));
    }

    @Override
    public Maybe<io.gravitee.am.model.Email> getEmailTemplate(io.gravitee.am.model.Template template, User user) {
        return emailManager.getEmail(template, user, getDefaultSubject(template), getDefaultExpireAt(template));
    }

    private void sendEmail(Email email, User user, DomainBasedDictionaryProvider dictionaryProvider) {
        if (enabled) {
            try {
                final var locale = preferredLanguage(user, Locale.ENGLISH);
                final var emailToSend = processEmailTemplate(email, locale, dictionaryProvider);
                emailService.send(emailToSend);
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                        .reference(Reference.domain(user.getReferenceId()))
                        .client(ADMIN_CLIENT).email(email)
                        .user(user));
            } catch (final Exception ex) {
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                        .reference(Reference.domain(user.getReferenceId()))
                        .client(ADMIN_CLIENT)
                        .email(email)
                        .throwable(ex));
            }
        }
    }

    @Override
    public Maybe<Email> getFinalEmail(Domain domain, Application client, io.gravitee.am.model.Template template, User user, Map<String, Object> params) {
        // get raw email template
        return emailManager.getEmail(template, ReferenceType.DOMAIN, domain.getId(), user, getDefaultSubject(template), getDefaultExpireAt(template))
                .flatMap(emailTemplate -> {
                    // prepare email
                    final var email = prepareEmail(domain, client, template, emailTemplate, user);

                    if (email.getParams() != null) {
                        email.getParams().putAll(params);
                    } else {
                        email.setParams(params);
                    }

                    // send email
                    var locale = preferredLanguage(user, Locale.ENGLISH);
                    return refreshDomainDictionaries(domain)
                            .map(provider -> processEmailTemplate(email, locale, provider)).toMaybe();
                });
    }

    private Email processEmailTemplate(Email email, Locale locale, DomainBasedDictionaryProvider dictionaryProvider) throws IOException, TemplateException {
        final Template template = freemarkerConfiguration.getTemplate(email.getTemplate());
        final Email emailToSend = new Email(email);

        if (!StringUtils.isEmpty(email.getFromName())) {
            final Template fromNameTemplate = new Template("fromName", email.getFromName(), freemarkerConfiguration);
            final String fromName = processTemplate(fromNameTemplate, email.getParams(), locale, dictionaryProvider);
            emailToSend.setFromName(fromName);
        }

        // compute email subject
        final Template subjectTemplate = new Template("subject", email.getSubject(), freemarkerConfiguration);
        final String subject = processTemplate(subjectTemplate, email.getParams(), locale, dictionaryProvider);
        emailToSend.setSubject(subject);
        
        // compute email content
        final String content = processTemplate(template, email.getParams(), locale, dictionaryProvider);
        emailToSend.setContent(content);
        return emailToSend;
    }

    private String processTemplate(Template plainTextTemplate, Map<String, Object> params, Locale preferredLanguage, DomainBasedDictionaryProvider dictionaryProvider) throws TemplateException, IOException {
        var result = new StringWriter(1024);

        var dataModel = new HashMap<>(params);
        dataModel.put(FreemarkerMessageResolver.METHOD_NAME, new FreemarkerMessageResolver(this.computeDictionaryProvider(dictionaryProvider).getDictionaryFor(preferredLanguage)));

        var env = plainTextTemplate.createProcessingEnvironment(new SimpleHash(dataModel,
                new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_22).build()), result);
        env.process();

        return result.toString();
    }

    public DictionaryProvider computeDictionaryProvider(DomainBasedDictionaryProvider dictionaryProvider) {
        if (dictionaryProvider != null) {
            return new CompositeDictionaryProvider(dictionaryProvider, emailService.getDefaultDictionaryProvider());
        } else {
            return emailService.getDefaultDictionaryProvider();
        }
    }

    private Email prepareEmail(Domain domain, Application client, io.gravitee.am.model.Template template, io.gravitee.am.model.Email emailTemplate, User user) {
        Map<String, Object> params = prepareEmailParams(domain, client, user, emailTemplate.getExpiresAfter(), template.redirectUri(), template);
        return new EmailBuilder()
                .to(user.getEmail())
                .from(emailTemplate.getFrom())
                .fromName(emailTemplate.getFromName())
                .subject(emailTemplate.getSubject())
                .template(emailTemplate.getTemplate())
                .params(params)
                .build();
    }

    private Map<String, Object> prepareEmailParams(Domain domain, Application client, User user, Integer expiresAfter, String redirectUri, io.gravitee.am.model.Template template) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        Instant now = Instant.now();
        claims.put(Claims.IAT, now.getEpochSecond());
        claims.put(Claims.EXP, now.plusSeconds(expiresAfter).getEpochSecond());
        claims.put(Claims.SUB, user.getId());
        if (user.getClient() != null) {
            claims.put(Claims.AUD, user.getClient());
        }

        if (template == io.gravitee.am.model.Template.REGISTRATION_CONFIRMATION) {
            claims.put(ConstantKeys.CLAIM_TOKEN_PURPOSE, TokenPurpose.REGISTRATION_CONFIRMATION);
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

        params.put("user", new UserProperties(user, false));
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

    private Boolean enabled() {
        return Boolean.valueOf(environment.getProperty("email.enabled", "false"));
    }

    private String registrationSubject() {
        return environment.getProperty("user.registration.email.subject", "New user registration");
    }

    private Integer registrationExpireAfter() {
        return Integer.valueOf(environment.getProperty("user.registration.token.expire-after", "86400"));
    }

    private String registrationVerifySubject() {
        return environment.getProperty("user.registration.verify.email.subject", "New user registration");
    }

    private Integer registrationVerifyExpireAfter() {
        return Integer.valueOf(environment.getProperty("user.registration.verify.token.expire-after", "604800"));
    }

    private String certificateExpirySubject() {
        return environment.getProperty("services.certificate.expiryEmailSubject", "Certificate will expire soon");
    }

}
