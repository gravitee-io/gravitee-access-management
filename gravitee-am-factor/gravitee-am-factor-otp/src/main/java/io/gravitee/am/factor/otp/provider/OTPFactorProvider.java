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
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.factor.otp.utils.TOTP.TIME_STEP;
import static io.gravitee.am.factor.otp.utils.TOTP.generateTOTP;
import static io.gravitee.am.factor.utils.SharedSecret.base32Str2Hex;
import static java.lang.System.currentTimeMillis;
import static java.util.stream.LongStream.range;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OTPFactorProvider implements FactorProvider {

    private static final Logger logger = LoggerFactory.getLogger(OTPFactorProvider.class);
    private static final int NUMBER_OF_VALIDATIONS = 3;

    @Autowired
    private OTPFactorConfiguration otpFactorConfiguration;

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);

        return Completable.create(emitter -> {
            try {
                tryEvaluation(code, enrolledFactor, emitter);
            } catch (Exception ex) {
                logger.error("An error occurs while validating 2FA code", ex);
                emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
            }
        });
    }

    private static void tryEvaluation(String code, EnrolledFactor enrolledFactor, CompletableEmitter emitter) {
        var enrolledFactorValue = enrolledFactor.getSecurity().getValue();
        if (isCodeInvalid(code, enrolledFactorValue)) {
            emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
        }
        emitter.onComplete();
    }

    /**
     * Checking if code is valid while handling clock-drifts
     * See <a href="https://datatracker.ietf.org/doc/html/rfc6238#section-6">Resynchronization</a>
     * As per the RFC:
     *    Because of possible clock drifts between a client and a validation
     *    server, we RECOMMEND that the validator be set with a specific limit
     *    to the number of time steps a prover can be "out of synch" before
     *    being rejected.
     *    [...]
     *    If the time step is 30 seconds as recommended, and the validator is set
     *    to only accept two time steps backward, then the maximum elapsed time drift
     *    would be around 89 seconds
     **/
    private static boolean isCodeInvalid(String code, String value) {
        final long now = currentTimeMillis();
        return range(0, NUMBER_OF_VALIDATIONS).mapToObj(offset -> {
                    final long timeOffset = offset * TIME_STEP - (offset == NUMBER_OF_VALIDATIONS - 1 ? 1000 : 0);
                    final long time = (now - timeOffset) / TIME_STEP;
                    return generateTOTP(base32Str2Hex(value), time);
                }).noneMatch(code::equals);
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
    public boolean checkSecurityFactor(EnrolledFactor factor) {
        boolean valid = true;
        if (factor != null) {
            EnrolledFactorSecurity securityFactor = factor.getSecurity();
            if (securityFactor == null || securityFactor.getValue() == null) {
                logger.warn("No shared secret in form - did you forget to include shared secret value ?");
                valid = false;
            }
        }
        return valid;
    }

    @Override
    public Maybe<String> generateQrCode(User user, EnrolledFactor enrolledFactor) {
        return Maybe.fromCallable(() -> {
            final String key = enrolledFactor.getSecurity().getValue();
            final String username = user.getUsername();
            return QRCode.generate(QRCode.generateURI(key, otpFactorConfiguration.getIssuer(), username), 200, 200);
        });
    }
}
