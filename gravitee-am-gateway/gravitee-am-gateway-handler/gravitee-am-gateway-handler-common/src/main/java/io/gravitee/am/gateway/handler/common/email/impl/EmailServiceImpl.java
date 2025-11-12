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
package io.gravitee.am.gateway.handler.common.email.impl;

import com.google.common.base.Strings;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.common.email.EmailBuilder;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.TokenPurpose;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.email.EmailManager;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.utils.FreemarkerDataHelper;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.i18n.CompositeDictionaryProvider;
import io.gravitee.am.service.i18n.DictionaryProvider;
import io.gravitee.am.service.i18n.FreemarkerMessageResolver;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.EmailAuditBuilder;
import io.vertx.rxjava3.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.common.oauth2.Parameters.CLIENT_ID;
import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static io.gravitee.am.service.utils.UserProfileUtils.preferredLanguage;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailServiceImpl implements EmailService {

    private final boolean enabled;
    private final String resetPasswordSubject;
    private final Integer resetPasswordExpireAfter;
    private final String blockedAccountSubject;
    private final Integer blockedAccountExpireAfter;
    private final String mfaChallengeSubject;
    private final Integer mfaChallengeExpireAfter;
    private final String mfaVerifyAttemptSubject;
    private final String registrationVerifySubject;
    private final int userRegistrationExpireAfter;
    @Autowired
    private EmailManager emailManager;

    @Autowired
    private io.gravitee.am.service.EmailService emailService;

    @Autowired
    private Configuration freemarkerConfiguration;

    @Autowired
    private Domain domain;

    @Autowired
    private AuditService auditService;

    @Lazy // Need to be lazy loaded to ensure jwt buider is instantiated with all resolved configuration included eventual secrets.
    @Autowired
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Autowired
    private DomainReadService domainService;

    @Autowired
    private GraviteeMessageResolver graviteeMessageResolver;

    public EmailServiceImpl(
            boolean enabled,
            String resetPasswordSubject,
            int resetPasswordExpireAfter,
            String blockedAccountSubject,
            int blockedAccountExpireAfter,
            String mfaChallengeSubject,
            int mfaChallengeExpireAfter,
            String mfaVerifyAttemptSubject,
            String registrationVerifySubject,
            int userRegistrationExpiresAfter) {
        this.enabled = enabled;
        this.resetPasswordSubject = resetPasswordSubject;
        this.resetPasswordExpireAfter = resetPasswordExpireAfter;
        this.blockedAccountSubject = blockedAccountSubject;
        this.blockedAccountExpireAfter = blockedAccountExpireAfter;
        this.mfaChallengeSubject = mfaChallengeSubject;
        this.mfaChallengeExpireAfter = mfaChallengeExpireAfter;
        this.mfaVerifyAttemptSubject = mfaVerifyAttemptSubject;
        this.registrationVerifySubject = registrationVerifySubject;
        this.userRegistrationExpireAfter = userRegistrationExpiresAfter;
    }

    @Override
    public void send(io.gravitee.am.model.Template template, User user, Client client, MultiMap queryParams) {
        if (enabled) {
            // get raw email template
            io.gravitee.am.model.Email emailTemplate = getEmailTemplate(template, client);
            // prepare email
            Email email = prepareEmail(template, emailTemplate, user, client, queryParams);
            // send email
            sendEmail(email, user, client);
        }
    }

    @Override
    public void send(Email email) {
        if (enabled) {
            try {

                Locale language = Locale.ENGLISH;
                if (email.getParams().containsKey(ConstantKeys.USER_CONTEXT_KEY)) {
                    language = preferredLanguage((User) email.getParams().get(ConstantKeys.USER_CONTEXT_KEY), Locale.ENGLISH);
                }

                // sanitize data
                final Map<String, Object> params = FreemarkerDataHelper.generateData(email.getParams());

                // compute email to
                final List<String> to = new ArrayList<>();
                for (String emailTo : email.getTo()) {
                    to.add(processTemplate(
                            new Template("to", new StringReader(emailTo), freemarkerConfiguration),
                            params, language));
                }
                // compute email from
                final String from = processTemplate(
                        new Template("from", new StringReader(email.getFrom()), freemarkerConfiguration),
                        params, language);
                // compute email fromName
                final String fromName = Strings.isNullOrEmpty(email.getFromName()) ? null : processTemplate(
                        new Template("fromName", new StringReader(email.getFromName()), freemarkerConfiguration),
                        params, language);
                // compute email subject
                final String subject = processTemplate(
                        new Template("subject", new StringReader(email.getSubject()), freemarkerConfiguration),
                        params, language);
                // compute email content
                final String content = processTemplate(
                        new Template("content", new StringReader(email.getContent()), freemarkerConfiguration),
                        params, language);
                // send the email
                final Email emailToSend = new Email(email);
                emailToSend.setFrom(from);
                emailToSend.setFromName(fromName);
                emailToSend.setTo(to.toArray(new String[0]));
                emailToSend.setSubject(subject);
                emailToSend.setContent(content);
                emailService.send(emailToSend);
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .email(email));
            } catch (Exception ex) {
                auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .email(email)
                        .throwable(ex));
            }
        }
    }

    private void sendEmail(Email email, User user, Client client) {
        try {
            final Locale preferredLanguage = preferredLanguage(user, Locale.ENGLISH);
            final Map<String, Object> params = email.getParams();
            final Email emailToSend = new Email(email);

            // compute email - from
            final Template fromTemplate = new Template("from", new StringReader(email.getFrom()), freemarkerConfiguration);
            final String from = processTemplate(fromTemplate, params, preferredLanguage);
            email.setFrom(from);

            if (!StringUtils.isEmpty(email.getFromName())) {
                // compute email - fromName
                final Template fromNameTemplate = new Template("fromName", new StringReader(email.getFromName()), freemarkerConfiguration);
                final String fromName = processTemplate(fromNameTemplate, params, preferredLanguage);
                email.setFromName(fromName);
            }

            // compute email subject
            final Template plainTextTemplate = new Template("subject", new StringReader(email.getSubject()), freemarkerConfiguration);
            final String subject = processTemplate(plainTextTemplate, params, preferredLanguage);
            email.setSubject(subject);

            // compute email content
            final Template template = freemarkerConfiguration.getTemplate(email.getTemplate());
            final String content = processTemplate(template, params, preferredLanguage);
            email.setContent(content);

            emailService.send(emailToSend);
            auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                    .reference(Reference.domain(domain.getId()))
                    .client(client)
                    .email(email)
                    .user(user));
        } catch (final Exception ex) {
            auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                    .reference(Reference.domain(domain.getId()))
                    .client(client)
                    .email(email)
                    .throwable(ex));
        }
    }

    private Email prepareEmail(io.gravitee.am.model.Template template, io.gravitee.am.model.Email emailTemplate, User user, Client client, MultiMap queryParams) {
        Map<String, Object> params = prepareEmailParams(user, client, emailTemplate.getExpiresAfter(), template, queryParams);
        return new EmailBuilder()
                .to(user.getEmail())
                .from(emailTemplate.getFrom())
                .fromName(emailTemplate.getFromName())
                .subject(emailTemplate.getSubject())
                .template(emailTemplate.getTemplate())
                .params(params)
                .build();
    }

    private Map<String, Object> prepareEmailParams(User user, Client client, Integer expiresAfter, io.gravitee.am.model.Template template, MultiMap queryParams) {
        // generate a JWT to store user's information and for security purpose
        final Map<String, Object> claims = new HashMap<>();
        Instant now = Instant.now();
        claims.put(Claims.IAT, now.getEpochSecond());
        claims.put(Claims.EXP, now.plusSeconds(expiresAfter).getEpochSecond());
        claims.put(Claims.SUB, user.getId());
        if (client != null) {
            claims.put(Claims.AUD, client.getId());
        }

        if (client != null && !queryParams.contains(CLIENT_ID)) {
            queryParams.add(CLIENT_ID, encodeURIComponent(client.getClientId()));
        }

        getTokenPurpose(template)
                .ifPresent(purpose -> claims.put(ConstantKeys.CLAIM_TOKEN_PURPOSE, purpose));

        String token = jwtBuilder.sign(new JWT(claims));
        queryParams.add("token", token);

        Map<String, Object> params = new HashMap<>();
        params.put("user", new UserProperties(user, false));
        params.put("token", token);
        params.put("expireAfterSeconds", expiresAfter);
        params.put("domain", new DomainProperties(domain));

        if (client != null) {
            params.put("client", new ClientProperties(client));
        }

        params.put("url", domainService.buildUrl(domain, template.redirectUri(), queryParams));

        return params;
    }

    private Optional<TokenPurpose> getTokenPurpose(io.gravitee.am.model.Template template) {
        return switch (template) {
            case RESET_PASSWORD -> Optional.of(TokenPurpose.RESET_PASSWORD);
            case REGISTRATION_VERIFY -> Optional.of(TokenPurpose.REGISTRATION_VERIFY);
            // not UNSPECIFIED, because if the token has no particular purpose, we don't want it to contain this claim
            default -> Optional.empty();
        };

    }

    protected io.gravitee.am.model.Email getEmailTemplate(io.gravitee.am.model.Template template, Client client) {
        return emailManager.getEmail(getTemplateName(template, client), getDefaultSubject(template), getDefaultExpireAt(template));
    }

    @Override
    public EmailWrapper createEmail(io.gravitee.am.model.Template template, Client client, List<String> recipients, Map<String, Object> params, Locale preferredLanguage) throws IOException, TemplateException {
        io.gravitee.am.model.Email emailTpl = getEmailTemplate(template, client);
        params.putIfAbsent("expireAfterSeconds", emailTpl.getExpiresAfter());
        final long expiresAt = Instant.now().plus(emailTpl.getExpiresAfter(), ChronoUnit.SECONDS).toEpochMilli();
        params.putIfAbsent("expireAt", expiresAt);

        io.gravitee.am.common.email.Email email = new EmailBuilder()
                .from(emailTpl.getFrom())
                .fromName(emailTpl.getFromName())
                .template(emailTpl.getTemplate())
                .to(recipients.toArray(new String[recipients.size()]))
                .build();

        // compute email subject
        final Template plainTextTemplate = new Template("subject", new StringReader(emailTpl.getSubject()), freemarkerConfiguration);
        email.setSubject(processTemplate(plainTextTemplate, params, preferredLanguage));

        // compute email content
        final Template subjectTemplate = freemarkerConfiguration.getTemplate(email.getTemplate());
        email.setContent(processTemplate(subjectTemplate, params, preferredLanguage));

        EmailWrapper wrapper = new EmailWrapper(email);
        wrapper.setExpireAt(expiresAt);
        wrapper.setFromDefaultTemplate(emailTpl.isDefaultTemplate());
        return wrapper;
    }

    private String processTemplate(Template plainTextTemplate, Map<String, Object> params, Locale preferredLanguage) throws TemplateException, IOException {
        var result = new StringWriter(1024);

        var dataModel = new HashMap<>(params);
        dataModel.put(FreemarkerMessageResolver.METHOD_NAME, new FreemarkerMessageResolver(getDictionaryProvider().getDictionaryFor(preferredLanguage)));

        var env = plainTextTemplate.createProcessingEnvironment(dataModel, result);
        env.process();

        return result.toString();
    }

    public DictionaryProvider getDictionaryProvider() {
        if (graviteeMessageResolver.getDynamicDictionaryProvider() != null) {
            return new CompositeDictionaryProvider(graviteeMessageResolver.getDynamicDictionaryProvider(), emailService.getDefaultDictionaryProvider());
        } else {
            return emailService.getDefaultDictionaryProvider();
        }
    }

    private String getTemplateName(io.gravitee.am.model.Template template, Client client) {
        return template.template() + ((client != null) ? EmailManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
    }

    private String getDefaultSubject(io.gravitee.am.model.Template template) {
        switch (template) {
            case RESET_PASSWORD:
                return resetPasswordSubject;
            case BLOCKED_ACCOUNT:
                return blockedAccountSubject;
            case MFA_CHALLENGE:
                return mfaChallengeSubject;
            case VERIFY_ATTEMPT:
                return mfaVerifyAttemptSubject;
            case REGISTRATION_VERIFY:
                return registrationVerifySubject;
            default:
                throw new IllegalArgumentException(template.template() + " not found");
        }
    }

    private Integer getDefaultExpireAt(io.gravitee.am.model.Template template) {
        switch (template) {
            case RESET_PASSWORD:
                return resetPasswordExpireAfter;
            case BLOCKED_ACCOUNT:
                return blockedAccountExpireAfter;
            case MFA_CHALLENGE:
                return mfaChallengeExpireAfter;
            case VERIFY_ATTEMPT:
                return 0;
            case REGISTRATION_VERIFY:
                return userRegistrationExpireAfter;
            default:
                throw new IllegalArgumentException(template.template() + " not found");
        }
    }

}
