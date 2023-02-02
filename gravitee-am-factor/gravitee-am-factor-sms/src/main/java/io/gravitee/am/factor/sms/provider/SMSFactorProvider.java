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
package io.gravitee.am.factor.sms.provider;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.OTPFactorProvider;
import io.gravitee.am.factor.sms.SMSFactorConfiguration;
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.Message;
import io.gravitee.am.resource.api.MessageResourceProvider;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFALink;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SMSFactorProvider extends OTPFactorProvider {

    private static final Logger logger = LoggerFactory.getLogger(SMSFactorProvider.class);

    @Autowired
    private SMSFactorConfiguration configuration;

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        ResourceManager component = context.getComponent(ResourceManager.class);
        ResourceProvider provider = component.getResourceProvider(configuration.getGraviteeResource());
        if (provider instanceof MFAResourceProvider) {
            MFAResourceProvider mfaProvider = (MFAResourceProvider) provider;
            MFAChallenge challenge = new MFAChallenge(enrolledFactor.getChannel().getTarget(), code, context);
            return mfaProvider.verify(challenge);
        } else if (provider instanceof MessageResourceProvider) {
            return verifyOTP(enrolledFactor, configuration.getReturnDigits(), code);
        } else {
            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication  with type SMS"));
        }
    }

    @Override
    public Single<Enrollment> enroll(FactorContext factorContext) {
        return Single.defer(() -> {
            Enrollment enrollment = new Enrollment(configuration.countries());
            // generate shared secret if the resource used for this factor is not an MFA one
            if (!isMFAResourceProvider(factorContext)) {
                enrollment.setKey(SharedSecret.generate());
            }
            return Single.just(enrollment);
        });
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
        UserService userService = context.getComponent(UserService.class);
        if (provider instanceof MFAResourceProvider) {
            MFAResourceProvider mfaProvider = (MFAResourceProvider) provider;
            MFALink link = new MFALink(MFAType.SMS, enrolledFactor.getChannel().getTarget(), context);
            return mfaProvider.send(link);
        } else if (provider instanceof MessageResourceProvider) {
            try {
                // Code Expiration date is present, that mean the code has not been validated
                // check if the code has expired to know if we have to generate a new code or send the same
                if (enrolledFactor.getSecurity() != null && enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class) != null &&
                        Instant.now().isAfter(Instant.ofEpochMilli(enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class)))) {
                    incrementMovingFactor(enrolledFactor);
                }

                // register mfa code to make it available into the TemplateEngine values
                Map<String, Object> params = context.getTemplateValues();
                params.put(FactorContext.KEY_CODE, generateOTP(enrolledFactor, configuration.getReturnDigits()));

                // generate message
                Message message = new Message();
                message.setTarget(enrolledFactor.getChannel().getTarget());
                message.setContent(context.getTemplateEngine().getValue(configuration.getMessageBody(), String.class));
                return ((MessageResourceProvider) provider)
                        .sendMessage(message)
                        .andThen(Single.defer(() -> {
                            enrolledFactor.getSecurity().putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().plusSeconds(configuration.getExpiresAfter()).toEpochMilli());
                            return userService.addFactor(context.getUser().getId(), enrolledFactor, new DefaultUser(context.getUser()));
                        })).ignoreElement();
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                logger.error("Code generation fails", e);
                return Completable.error(new TechnicalException("Code can't be sent"));
            } catch (Exception e) {
                logger.error("SMS templating fails", e);
                return Completable.error(new TechnicalException("SMS can't be sent"));
            }
        } else {
            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication with type SMS"));
        }
    }

    @Override
    public boolean useVariableFactorSecurity(FactorContext factorContext) {
        // generate moving factor if the resource used for this factor is not an MFA one
        return !isMFAResourceProvider(factorContext);
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactor factor) {
        boolean valid = false;
        if (factor != null) {
            EnrolledFactorChannel enrolledFactorChannel = factor.getChannel();
            if (enrolledFactorChannel == null || enrolledFactorChannel.getTarget() == null) {
                logger.warn("No phone number in form");
            } else {
                // check phone format according to Factor configuration
                final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
                try {
                    Phonenumber.PhoneNumber phone = phoneNumberUtil.parse(enrolledFactorChannel.getTarget(), Phonenumber.PhoneNumber.CountryCodeSource.UNSPECIFIED.name());
                    for (String country : configuration.countries()) {
                        if (phoneNumberUtil.isValidNumberForRegion(phone, country.toUpperCase(Locale.ROOT))) {
                            valid = true;
                            break;
                        }
                    }

                    if (!valid) {
                        logger.warn("Invalid phone number");
                    }
                } catch (NumberParseException e) {
                    logger.warn("Invalid phone number", e);
                }
            }
        }
        return valid;
    }

    private boolean isMFAResourceProvider(FactorContext factorContext) {
        ResourceManager resourceManager = factorContext.getComponent(ResourceManager.class);
        ResourceProvider resourceProvider = resourceManager.getResourceProvider(configuration.getGraviteeResource());
        return (resourceProvider instanceof MFAResourceProvider);
    }
}
