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
package io.gravitee.am.factor.call.provider;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.call.CallFactorConfiguration;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFALink;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAType;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CallFactorProvider implements FactorProvider {

    private static final Logger logger = LoggerFactory.getLogger(CallFactorProvider.class);

    @Autowired
    private CallFactorConfiguration configuration;

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        ResourceManager component = context.getComponent(ResourceManager.class);
        ResourceProvider provider = component.getResourceProvider(configuration.getGraviteeResource());
        if (provider instanceof MFAResourceProvider) {
            var mfaProvider = (MFAResourceProvider) provider;
            var challenge = new MFAChallenge(enrolledFactor.getChannel().getTarget(), code, context);
            return mfaProvider.verify(challenge);
        } else {
            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication with type Call"));
        }
    }

    @Override
    public Single<Enrollment> enroll(String account) {
        return Single.just(new Enrollment(this.configuration.countries()));
    }

    @Override
    public Completable sendChallenge(FactorContext context) {
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        ResourceManager component = context.getComponent(ResourceManager.class);
        ResourceProvider provider = component.getResourceProvider(configuration.getGraviteeResource());
        if (provider instanceof MFAResourceProvider) {
            MFAResourceProvider mfaProvider = (MFAResourceProvider) provider;
            MFALink link = new MFALink(MFAType.CALL, enrolledFactor.getChannel().getTarget(), context);
            return mfaProvider.send(link);
        } else {
            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication  with type SMS"));
        }
    }

    @Override
    public boolean needChallengeSending() {
        return true;
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
}
