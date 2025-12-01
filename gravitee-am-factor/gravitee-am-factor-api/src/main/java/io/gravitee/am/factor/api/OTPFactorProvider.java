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
package io.gravitee.am.factor.api;

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.factor.utils.HOTP;
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Factor Provider used to generate OTP codes and manage OTP based factors security (moving factor, shared secret)
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public abstract class OTPFactorProvider implements FactorProvider {

    @Override
    public boolean useVariableFactorSecurity() {
        return true;
    }

    @Override
    public Single<EnrolledFactor> changeVariableFactorSecurity(EnrolledFactor enrolledFactor) {
        // if no moving factor, continue
        if (enrolledFactor.getSecurity() == null
                || enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class) == null) {
            return Single.just(enrolledFactor);
        }

        return Single.fromCallable(() -> {
            incrementMovingFactor(enrolledFactor);
            enrolledFactor.getSecurity().removeData(FactorDataKeys.KEY_EXPIRE_AT);
            return enrolledFactor;
        });
    }

    protected Completable verifyOTP(EnrolledFactor enrolledFactor, int returnDigits, String code) {
        return Completable.create(emitter -> {
            try {
                final String otpCode = generateOTP(enrolledFactor, returnDigits);
                if (!code.equals(otpCode)) {
                    log.debug("Invalid 2FA code, not the same value");
                    emitter.onError(invalid2faCodeException());
                    return;
                }
                // get last connection date of the user to test code
                Long expireAt = enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class);
                Instant now = Instant.now();
                if (expireAt == null || now.isAfter(Instant.ofEpochMilli(expireAt))) {
                    if (expireAt != null) {
                        long differenceMillis = now.toEpochMilli() - expireAt;
                        String msg = "Invalid 2FA code, expiry date " + Instant.ofEpochMilli(expireAt) + ". Current time: " + now + ", a difference of " + differenceMillis + " milliseconds.";
                        log.debug(msg);
                    } else {
                        log.debug("Invalid 2FA code, expiry date is null");
                    }
                    emitter.onError(invalid2faCodeException());
                    return;
                }
                emitter.onComplete();
            } catch (Exception ex) {
                log.error("An error occurs while validating 2FA code", ex);
                emitter.onError(invalid2faCodeException());
            }
        });
    }

    protected String generateOTP(EnrolledFactor enrolledFactor, int returnDigits) throws NoSuchAlgorithmException, InvalidKeyException {
        return HOTP.generateOTP(SharedSecret.base32Str2Bytes(enrolledFactor.getSecurity().getValue()),
                enrolledFactor.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class).longValue(),
                returnDigits, false, 0);
    }

    protected void incrementMovingFactor(EnrolledFactor factor) {
        long counter = factor.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class).longValue();
        factor.getSecurity().putData(FactorDataKeys.KEY_MOVING_FACTOR, counter + 1);
    }

    private InvalidCodeException invalid2faCodeException() {
        return new InvalidCodeException("Invalid 2FA Code");
    }
}
