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
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OTPFactorProviderTest {

    private static final String CODE = "886024";
    private static final String SHARED_SECRET = "RVVAGS23PDJNU3NPRBMUHFXU4OXZTNRA";

    private AtomicReference<Throwable> undeliverableException;

    @BeforeEach
    public void setUp() {
        undeliverableException = new AtomicReference<>();
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                undeliverableException.set(e);
            }
        });
    }

    @AfterEach
    public void tearDown() {
        // Reset RxJava error handler
        RxJavaPlugins.reset();
    }

    @Test
    public void shouldUseVariableFactorSecurity() {
        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        assertTrue(otpFactorProvider.useVariableFactorSecurity());
    }
    @Test
    public void shouldChangeVariableFactorSecurity() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 1);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);
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
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().plusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "abcdef", "", " ", "1234567"})
    public void shouldNotVerifyOTPCode_wrongCode(String code) {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().plusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, code).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidCodeException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotVerifyOTPCode_expiredCode() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().minusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidCodeException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotVerifyOTPCode_wrongSecret() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue("wrong-secret");
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().plusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();
        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidCodeException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCallEmitterOnlyOnce_whenCodeMismatch() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().plusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();

        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, "123456").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidCodeException.class);
        testObserver.assertNotComplete();

        assertNull(undeliverableException.get(),
            "UndeliverableException should not occur - emitter was called multiple times!");
    }

    @Test
    public void shouldCallEmitterOnlyOnce_whenCodeExpired() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().minusSeconds(10).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();

        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidCodeException.class);
        testObserver.assertNotComplete();

        assertNull(undeliverableException.get(),
            "UndeliverableException should not occur - emitter was called multiple times!");
    }

    @Test
    public void shouldCallEmitterOnlyOnce_whenExpirationIsNull() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();

        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidCodeException.class);
        testObserver.assertNotComplete();

        assertNull(undeliverableException.get(),
            "UndeliverableException should not occur - emitter was called multiple times!");
    }

    @Test
    public void shouldCallEmitterOnlyOnce_whenVerificationSucceeds() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().plusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();

        TestObserver<Void> testObserver = otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoErrors();
        testObserver.assertComplete();

        assertNull(undeliverableException.get(),
            "UndeliverableException should not occur - emitter was called multiple times!");
    }

    @Test
    public void shouldInvokeEmitterExactlyOnce_wrongCode() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().plusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        AtomicInteger emitterCallCount = new AtomicInteger(0);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();

        Completable completable = Completable.create(emitter -> {
            otpFactorProvider.verifyOTP(enrolledFactor, 6, "wrong-code")
                .subscribe(
                    () -> {
                        emitterCallCount.incrementAndGet();
                        emitter.onComplete();
                    },
                    error -> {
                        emitterCallCount.incrementAndGet();
                        emitter.onError(error);
                    }
                );
        });

        TestObserver<Void> testObserver = completable.test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        assertEquals(1, emitterCallCount.get(),
            "Emitter should be invoked exactly once, but was invoked " + emitterCallCount.get() + " times");
    }

    @Test
    public void shouldInvokeEmitterExactlyOnce_expiredCode() {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().minusSeconds(10).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);

        AtomicInteger emitterCallCount = new AtomicInteger(0);

        OTPFactorProvider otpFactorProvider = new DummyOTPFactorProvider();

        Completable completable = Completable.create(emitter -> {
            otpFactorProvider.verifyOTP(enrolledFactor, 6, CODE)
                .subscribe(
                    () -> {
                        emitterCallCount.incrementAndGet();
                        emitter.onComplete();
                    },
                    error -> {
                        emitterCallCount.incrementAndGet();
                        emitter.onError(error);
                    }
                );
        });

        TestObserver<Void> testObserver = completable.test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        assertEquals(1, emitterCallCount.get(),
            "Emitter should be invoked exactly once, but was invoked " + emitterCallCount.get() + " times");
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
