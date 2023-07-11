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
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OTPFactorProviderTest {

    private static final String CODE = "886024";
    private static final String SHARED_SECRET = "RVVAGS23PDJNU3NPRBMUHFXU4OXZTNRA";

    @Test
    public void shouldUseVariableFactorSecurity() {
        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        assertTrue(otpFactorProvider.useVariableFactorSecurity());
    }

    @Test
    public void shouldChangeVariableFactorSecurity() {
        EnrolledFactor enrolledFactor = getEnrolledFactor(Instant.now().toEpochMilli(), null, 1);
        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        TestObserver<EnrolledFactor> testObserver = otpFactorProvider.changeVariableFactorSecurity(enrolledFactor).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(eL -> eL.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Number.class) == null);
        testObserver.assertValue(eL -> eL.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class).longValue() == 2L);
    }

    @Test
    public void shouldVerifyOTPCode_nominalCase() {
        final long expirationTime = Instant.now().plusSeconds(60).toEpochMilli();
        EnrolledFactor enrolledFactor = getEnrolledFactor(expirationTime, SHARED_SECRET, 0);
        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();

        var testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @ParameterizedTest(name = "Must not verify code")
    @MethodSource("params_that_must_not_verify_code")
    public void must_not_verify_code(long expirationTime, String code, String secret, Class<? extends Throwable> expected) {
        EnrolledFactor enrolledFactor = getEnrolledFactor(expirationTime, secret, 0);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        var testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, code).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(expected);
        testObserver.assertNotComplete();
    }

    private static EnrolledFactor getEnrolledFactor(long expirationTime, String secret, int keyMovingFactor) {
        var enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(secret);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, keyMovingFactor);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, expirationTime);
        enrolledFactor.setSecurity(enrolledFactorSecurity);
        return enrolledFactor;
    }

    private static Stream<Arguments> params_that_must_not_verify_code() {
        final Instant now = Instant.now();
        return Stream.of(
                Arguments.of(now.plusSeconds(60).toEpochMilli(), "123456", SHARED_SECRET, InvalidCodeException.class),
                Arguments.of(now.minusSeconds(60).toEpochMilli(), CODE, SHARED_SECRET, InvalidCodeException.class),
                Arguments.of(now.plusSeconds(60).toEpochMilli(), CODE, "wrong-secret",InvalidCodeException.class)
        );
    }

    private static class DummyOTPFactorProvider extends OTPFactorProvider {

        @Override
        public Completable verify(FactorContext context) {
            return Completable.complete();
        }

        @Override
        public boolean checkSecurityFactor(EnrolledFactor securityFactor) {
            return false;
        }

        @Override
        public boolean needChallengeSending() {
            return false;
        }

        @Override
        public Completable sendChallenge(FactorContext context) {
            return Completable.complete();
        }
    }
}
