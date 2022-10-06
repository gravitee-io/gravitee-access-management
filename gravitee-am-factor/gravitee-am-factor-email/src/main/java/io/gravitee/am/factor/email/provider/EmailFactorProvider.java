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
package io.gravitee.am.factor.email.provider;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.email.EmailFactorConfiguration;
import io.gravitee.am.factor.email.utils.HOTP;
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.email.EmailSenderProvider;
import io.reactivex.Completable;
import io.reactivex.Single;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static java.util.Arrays.asList;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailFactorProvider implements FactorProvider {

    private static final Logger logger = LoggerFactory.getLogger(EmailFactorProvider.class);
    public static final String TEMPLATE_SUFFIX = ".html";

    @Autowired
    private EmailFactorConfiguration configuration;

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);

        return Completable.create(emitter -> {
            try {
                final String otpCode = generateOTP(enrolledFactor);
                if (!code.equals(otpCode)) {
                    emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
                }
                // get last connection date of the user to test code
                if (Instant.now().isAfter(Instant.ofEpochMilli(enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class)))) {
                    emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
                }
                emitter.onComplete();
            } catch (Exception ex) {
                logger.error("An error occurs while validating 2FA code", ex);
                emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
            }
        });

    }

    @Override
    public Single<Enrollment> enroll(String account) {
        return Single.fromCallable(() -> new Enrollment(SharedSecret.generate()));
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactor factor) {
        boolean valid = false;
        if (factor != null) {
            EnrolledFactorSecurity securityFactor = factor.getSecurity();
            if (securityFactor == null || securityFactor.getValue() == null) {
                logger.warn("No shared secret in form");
            } else {
                EnrolledFactorChannel enrolledFactorChannel = factor.getChannel();
                if (enrolledFactorChannel == null || enrolledFactorChannel.getTarget() == null) {
                    logger.warn("No email address in form");
                } else {
                    try {
                        InternetAddress internetAddress = new InternetAddress(enrolledFactorChannel.getTarget());
                        internetAddress.validate();
                        valid = true;
                    } catch (AddressException e) {
                        logger.warn("Email address is invalid", e);
                    }
                }
            }
        }
        return valid;
    }

    @Override
    public boolean needChallengeSending() {
        return true;
    }

    @Override
    public Completable sendChallenge(FactorContext context) {
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        ResourceManager component = context.getComponent(ResourceManager.class);
        ResourceProvider provider = component.getResourceProvider(configuration.getGraviteeResource());

        if (provider instanceof EmailSenderProvider) {

            return generateCodeAndSendEmail(context, (EmailSenderProvider) provider, enrolledFactor);

        } else {

            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication with type EMAIL"));
        }
    }

    private Completable generateCodeAndSendEmail(FactorContext context, EmailSenderProvider provider, EnrolledFactor enrolledFactor) {
        logger.debug("Generating factor code of {} digits", configuration.getReturnDigits());

        try {
            UserService userService = context.getComponent(UserService.class);
            EmailService emailService = context.getComponent(EmailService.class);

            // Code Expiration date is present, that mean the code has not been validated
            // check if the code has expired to know if we have to generate a new code or send the same
            if (enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class) != null &&
                    Instant.now().isAfter(Instant.ofEpochMilli(enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class)))) {
                incrementMovingFactor(enrolledFactor);
            }

            // register mfa code to make it available into the TemplateEngine values
            Map<String, Object> params = context.getTemplateValues();
            params.put(FactorContext.KEY_CODE, generateOTP(enrolledFactor));

            final String recipient = enrolledFactor.getChannel().getTarget();
            EmailService.EmailWrapper emailWrapper = emailService.createEmail(Template.MFA_CHALLENGE, context.getClient(), asList(recipient), params);

            return provider.sendMessage(emailWrapper.getEmail(), emailWrapper.isFromDefaultTemplate())
                    .andThen(Single.just(enrolledFactor)
                            .flatMap(ef -> {
                                ef.getSecurity().putData(FactorDataKeys.KEY_EXPIRE_AT, emailWrapper.getExpireAt());
                                return userService.addFactor(context.getUser().getId(), ef, new DefaultUser(context.getUser()));
                            }).ignoreElement());

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Code generation fails", e);
            return Completable.error(new TechnicalException("Code can't be sent"));
        } catch (Exception e) {
            logger.error("Email templating fails", e);
            return Completable.error(new TechnicalException("Email can't be sent"));
        }
    }

    String generateOTP(EnrolledFactor enrolledFactor) throws NoSuchAlgorithmException, InvalidKeyException {
        return HOTP.generateOTP(SharedSecret.base32Str2Bytes(enrolledFactor.getSecurity().getValue()),
                enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class).longValue(),
                configuration.getReturnDigits(), false, 0);
    }

    @Override
    public boolean useVariableFactorSecurity() {
        return true;
    }

    @Override
    public Single<EnrolledFactor> changeVariableFactorSecurity(EnrolledFactor factor) {
        return Single.fromCallable(() -> {
            incrementMovingFactor(factor);
            factor.getSecurity().removeData(FactorDataKeys.KEY_EXPIRE_AT);
            return factor;
        });
    }

    private void incrementMovingFactor(EnrolledFactor factor) {
        long counter = factor.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class).longValue();
        factor.getSecurity().putData(FactorDataKeys.KEY_MOVING_FACTOR, counter + 1);
    }
}
