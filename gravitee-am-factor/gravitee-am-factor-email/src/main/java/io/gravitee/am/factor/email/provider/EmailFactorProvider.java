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

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.email.EmailFactorConfiguration;
import io.gravitee.am.factor.email.utils.CodeGenerator;
import io.gravitee.am.factor.email.utils.HOTP;
import io.gravitee.am.factor.email.utils.SharedSecret;
import io.gravitee.am.gateway.handler.resource.ResourceManager;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.email.EmailSenderProvider;
import io.gravitee.am.resource.api.email.Message;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFALink;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAType;
import io.gravitee.el.TemplateEngine;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailFactorProvider implements FactorProvider, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(EmailFactorProvider.class);

    @Autowired
    private EmailFactorConfiguration configuration;

    private CodeGenerator codeGenerator;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.codeGenerator = new CodeGenerator(configuration.getReturnDigits());
    }

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);

        ResourceManager component = context.getComponent(ResourceManager.class);
        ResourceProvider provider = component.getResourceProvider(configuration.getGraviteeResource());

        if (!configuration.isMfaResource()) {

            return Completable.create(emitter -> {
                try {
                    final String otpCode = generateOTP(enrolledFactor);
                    if (!code.equals(otpCode)) {
                        emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
                    }
                    if (Instant.now().isAfter(Instant.ofEpochMilli(enrolledFactor.getUpdatedAt().getTime()).plus(configuration.getTtl(), ChronoUnit.MINUTES))) {
                        emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
                    }
                    emitter.onComplete();
                } catch (Exception ex) {
                    logger.error("An error occurs while validating 2FA code", ex);
                    emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
                }
            });

        } else if (configuration.isMfaResource() && provider instanceof MFAResourceProvider) {

            MFAResourceProvider mfaProvider = (MFAResourceProvider)provider;
            MFAChallenge challenge = new MFAChallenge(enrolledFactor.getChannel().getTarget(), code);
            return mfaProvider.verify(challenge);

        } else {

            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication with type EMAIL"));
        }
    }

    @Override
    public Single<Enrollment> enroll(String account) {
        return Single.fromCallable(() -> new Enrollment(SharedSecret.generate()));
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactor  factor) {
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

        if (!configuration.isMfaResource() && provider instanceof EmailSenderProvider) {

            return generateCodeAndSendEmail(context, (EmailSenderProvider) provider, enrolledFactor);

        } else if (configuration.isMfaResource() && provider instanceof MFAResourceProvider) {

            final String recipient = enrolledFactor.getChannel().getTarget();
            MFAResourceProvider mfaProvider = (MFAResourceProvider)provider;
            MFALink link = new MFALink(MFAType.EMAIL, recipient);
            return mfaProvider.send(link);

        } else {

            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication with type EMAIL"));
        }
    }

    private Completable generateCodeAndSendEmail(FactorContext context, EmailSenderProvider provider, EnrolledFactor enrolledFactor) {
        logger.debug("Generating factor code of {} digits", configuration.getReturnDigits());


        try {
            final String recipient = enrolledFactor.getChannel().getTarget();
            // register mfa code to make it available into the TemplateEngine values
            context.registerData(FactorContext.KEY_CODE, generateOTP(enrolledFactor));
            // Generate template before creating the code into repositories to avoid useless entries in case of template error
            TemplateEngine templateEngine = context.getTemplateEngine();
            String content = templateEngine.getValue(configuration.getTemplate(), String.class);
            String subject = templateEngine.getValue(configuration.getSubject(), String.class);

            return provider.sendMessage(new Message(recipient, content, subject, configuration.getContentType()));
        } catch (NoSuchAlgorithmException| InvalidKeyException e) {
            logger.error("Code generation fails", e);
            return Completable.error(new TechnicalException("Code can't be sent"));
        } catch (Exception e) {
            logger.error("Email templating fails", e);
            return Completable.error(new TechnicalException("Email can't be sent"));
        }
    }

    String generateOTP(EnrolledFactor enrolledFactor) throws NoSuchAlgorithmException, InvalidKeyException {
        return HOTP.generateOTP(SharedSecret.base32Str2Hex(enrolledFactor.getSecurity().getValue()), enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Integer.class), configuration.getReturnDigits(), false, 0);
    }

    @Override
    public boolean useVariableFactorSecurity() {
        return !this.configuration.isMfaResource();
    }

    @Override
    public Single<EnrolledFactor> changeVariableFactorSecurity(EnrolledFactor factor) {
        return Single.fromCallable(() -> {
            if (!this.configuration.isMfaResource()) {
                int counter = factor.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Integer.class);
                factor.getSecurity().putData(FactorDataKeys.KEY_MOVING_FACTOR, counter + 1);
            }
            return factor;
        });
    }
}
