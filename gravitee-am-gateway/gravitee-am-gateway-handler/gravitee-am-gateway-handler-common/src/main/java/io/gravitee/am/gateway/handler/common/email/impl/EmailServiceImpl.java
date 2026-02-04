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
import io.gravitee.am.common.exception.email.EmailDroppedException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.TokenPurpose;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.email.EmailContainer;
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
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.exception.BatchEmailException;
import io.gravitee.am.service.i18n.CompositeDictionaryProvider;
import io.gravitee.am.service.i18n.DictionaryProvider;
import io.gravitee.am.service.i18n.FreemarkerMessageResolver;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.EmailAuditBuilder;
import io.vertx.rxjava3.core.MultiMap;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.gravitee.am.common.oauth2.Parameters.CLIENT_ID;
import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;
import static io.gravitee.am.service.utils.UserProfileUtils.preferredLanguage;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
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
    private final int userRegistrationVerifyExpiresAfter;
    private final String registrationConfirmationSubject;
    private final int userRegistrationConfirmationVerifyExpiresAfter;
    private final String userMagicLinkLoginSubject;
    private final int userMagicLinkLoginExpiresAfter;

    private final EmailDroppedException droppedException = new EmailDroppedException("Email not delivered due to staging persistence issue");

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

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

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
            int userRegistrationVerifyExpiresAfter,
            String registrationConfirmationSubject,
            int userRegistrationConfirmationVerifyExpiresAfter,
            String userMagicLinkLoginSubject,
            int userMagicLinkLoginExpiresAfter) {
        this.enabled = enabled;
        this.resetPasswordSubject = resetPasswordSubject;
        this.resetPasswordExpireAfter = resetPasswordExpireAfter;
        this.blockedAccountSubject = blockedAccountSubject;
        this.blockedAccountExpireAfter = blockedAccountExpireAfter;
        this.mfaChallengeSubject = mfaChallengeSubject;
        this.mfaChallengeExpireAfter = mfaChallengeExpireAfter;
        this.mfaVerifyAttemptSubject = mfaVerifyAttemptSubject;
        this.registrationVerifySubject = registrationVerifySubject;
        this.userRegistrationVerifyExpiresAfter = userRegistrationVerifyExpiresAfter;
        this.registrationConfirmationSubject = registrationConfirmationSubject;
        this.userRegistrationConfirmationVerifyExpiresAfter = userRegistrationConfirmationVerifyExpiresAfter;
        this.userMagicLinkLoginSubject = userMagicLinkLoginSubject;
        this.userMagicLinkLoginExpiresAfter = userMagicLinkLoginExpiresAfter;
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
    public List<EmailContainer> batch(List<EmailContainer> containers, int maxAttempt) {
        if (containers == null || containers.isEmpty()) {
            return List.of();
        }

        if (enabled) {
            var emailsToSend = containers.stream().map(container -> {
                Email email = null;
                try {
                    var template = io.gravitee.am.model.Template.valueOf(container.stagingEmail().getEmailTemplateName());
                    // get raw email template
                    io.gravitee.am.model.Email emailTemplate = getEmailTemplate(template, container.client());
                    // prepare email
                    email = prepareEmail(template, emailTemplate, container.user(), container.client(), MultiMap.caseInsensitiveMultiMap());

                    return container.with(prepareEmailToSend(email, container.user()));
                } catch (Exception ex) {
                    log.warn("Unable to prepare email for user [{}], batch will exclude it and it will be removed from staging collection", container.user().getUsername(), ex);
                    // mark as processed to remove the entry from the staging collection
                    container.stagingEmail().markAsProcessed();
                    auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                            .reference(Reference.domain(domain.getId()))
                            .email(email) // email() method check ignore null value
                            .throwable(ex));
                    return null;
                }
            }).filter(Objects::nonNull)
            // It may happen that multiple users have the same email address
            // we group them together. In case of error all users will be notified
            // as we do not have a way to uniquely identify the user in that case
            .collect(Collectors.groupingBy(container -> container.user().getEmail()));

            try {
                emailService.batch(emailsToSend.values().stream().flatMap(List::stream).map(EmailContainer::email).toList());
                emailsToSend.values().stream().flatMap(List::stream).forEach(container -> {
                    container.stagingEmail().markAsProcessed();
                    log.debug("Email staging process {}", container.stagingEmail());
                    gatewayMetricProvider.incrementProcessedStagingEmails(true);
                    traceSuccessfulEmail(container.email(), container.user(), container.client());
                });
            } catch (BatchEmailException batchEx) {
                batchEx.getEmails().forEach(email -> {
                    Optional.ofNullable(emailsToSend.get(email)).ifPresent(failureContainers ->
                        failureContainers.forEach(container -> {
                            container.stagingEmail().incrementAttempts();
                            if (container.stagingEmail().getAttempts() >= maxAttempt) {
                                // maxAttempt reached, mark it as processed
                                // and trace the failure in the audit logs
                                container.stagingEmail().markAsProcessed();
                                log.warn("Send {} email failed for user {}, max attempts have been reach", container.stagingEmail().getEmailTemplateName(), email);
                                gatewayMetricProvider.incrementProcessedStagingEmails(false);
                                traceFailureEmail(container.email(), container.user(), container.client(), batchEx);
                            } else {
                                log.info("Send {} email failed for user {}", container.stagingEmail().getEmailTemplateName(), email);
                            }
                        })
                    );
                });

                // create an audit for all the email missing from the exception, they are processed
                emailsToSend.forEach((email, containerValues) -> {
                    if (!batchEx.getEmails().contains(email)) {
                        containerValues.forEach(successfulContainer -> {
                            successfulContainer.stagingEmail().markAsProcessed();
                            gatewayMetricProvider.incrementProcessedStagingEmails(true);
                            traceSuccessfulEmail(successfulContainer.email(), successfulContainer.user(), successfulContainer.client());
                        });
                    }
                });
            }
        }

        return containers;
    }

    @Override
    public void send(Email email) {
        if (enabled) {
            try {
                final Email emailToSend = prepareEmailToSend(email);
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

    private Email prepareEmailToSend(Email email) throws TemplateException, IOException {
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
        return emailToSend;
    }

    private void sendEmail(Email email, User user, Client client) {
        try {
            final Email emailToSend = prepareEmailToSend(email, user);
            emailService.send(emailToSend);
            traceSuccessfulEmail(email, user, client);
        } catch (final Exception ex) {
            traceFailureEmail(email, user, client, ex);
        }
    }

    private Email prepareEmailToSend(Email email, User user) throws IOException, TemplateException {
        final Locale preferredLanguage = preferredLanguage(user, Locale.ENGLISH);
        final Map<String, Object> params = email.getParams();
        final Email emailToSend = new Email(email);

        if (!StringUtils.isEmpty(email.getFromName())) {
            // compute email - fromName
            final Template fromNameTemplate = new Template("fromName", email.getFromName(), freemarkerConfiguration);
            final String fromName = processTemplate(fromNameTemplate, params, preferredLanguage);
            emailToSend.setFromName(fromName);
        }

        // compute email subject
        final Template plainTextTemplate = new Template("subject", email.getSubject(), freemarkerConfiguration);
        final String subject = processTemplate(plainTextTemplate, params, preferredLanguage);
        emailToSend.setSubject(subject);

        // compute email content
        final Template template = freemarkerConfiguration.getTemplate(email.getTemplate());
        final String content = processTemplate(template, params, preferredLanguage);
        emailToSend.setContent(content);
        return emailToSend;
    }

    private void traceFailureEmail(Email email, User user, Client client, Exception ex) {
        auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                .reference(Reference.domain(domain.getId()))
                .client(client)
                .email(email)
                .user(user)
                .throwable(ex));
    }

    private void traceSuccessfulEmail(Email email, User user, Client client) {
        auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                .reference(Reference.domain(domain.getId()))
                .client(client)
                .email(email)
                .user(user));
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
        if(queryParams != null && queryParams.contains(Parameters.SESSION_ID) ) {
            claims.put(Claims.SESSION_ID, queryParams.get(Parameters.SESSION_ID));
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
        return switch (template) {
            case RESET_PASSWORD -> resetPasswordSubject;
            case BLOCKED_ACCOUNT -> blockedAccountSubject;
            case MFA_CHALLENGE -> mfaChallengeSubject;
            case VERIFY_ATTEMPT -> mfaVerifyAttemptSubject;
            case REGISTRATION_VERIFY -> registrationVerifySubject;
            case REGISTRATION_CONFIRMATION -> registrationConfirmationSubject;
            case MAGIC_LINK -> userMagicLinkLoginSubject;
            default -> throw new IllegalArgumentException(template.template() + " not found");
        };
    }

    private Integer getDefaultExpireAt(io.gravitee.am.model.Template template) {
        return switch (template) {
            case RESET_PASSWORD -> resetPasswordExpireAfter;
            case BLOCKED_ACCOUNT -> blockedAccountExpireAfter;
            case MFA_CHALLENGE -> mfaChallengeExpireAfter;
            case VERIFY_ATTEMPT -> 0;
            case REGISTRATION_VERIFY -> userRegistrationVerifyExpiresAfter;
            case REGISTRATION_CONFIRMATION -> userRegistrationConfirmationVerifyExpiresAfter;
            case MAGIC_LINK -> userMagicLinkLoginExpiresAfter;
            default -> throw new IllegalArgumentException(template.template() + " not found");
        };
    }

    @Override
    public void traceEmailEviction(User user, Client client, io.gravitee.am.model.Template template) {
        Email email = new Email();
        email.setTemplate(getTemplateName(template, client));
        auditService.report(AuditBuilder.builder(EmailAuditBuilder.class)
                .reference(Reference.domain(domain.getId()))
                .client(client)
                .user(user)
                .email(email)
                .throwable(droppedException));
    }
}
