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
package io.gravitee.am.factor.otp.provider;

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.otp.OTPFactorConfiguration;
import io.gravitee.am.factor.otp.utils.QRCode;
import io.gravitee.am.factor.otp.utils.SharedSecret;
import io.gravitee.am.factor.otp.utils.TOTP;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OTPFactorProvider implements FactorProvider {

    private static final Logger logger = LoggerFactory.getLogger(OTPFactorProvider.class);

    @Autowired
    private OTPFactorConfiguration otpFactorConfiguration;

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);

        return Completable.create(emitter -> {
            try {
                final String otpCode = TOTP.generateTOTP(SharedSecret.base32Str2Hex(enrolledFactor.getSecurity().getValue()));
                if (!code.equals(otpCode)) {
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
        return Single.fromCallable(() -> {
            final String key = SharedSecret.generate();
            final String barCode = QRCode.generate(QRCode.generateURI(key, otpFactorConfiguration.getIssuer(), account), 200, 200);
            return new Enrollment(key, barCode);
        });
    }

    @Override
    public boolean needChallengeSending() {
        return false;
    }

    @Override
    public Completable sendChallenge(FactorContext context) {
        // OTP Challenge not need to be sent
        return Completable.complete();
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactorSecurity securityFactor) {
        boolean valid = true;
        if (securityFactor == null || securityFactor.getValue() == null) {
            logger.warn("No shared secret in form - did you forget to include shared secret value ?");
            valid = false;
        }
        return valid;
    }
}
