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
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.otp.OTPFactorConfiguration;
import io.gravitee.am.factor.otp.utils.TOTP;
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.factor.api.FactorContext.KEY_ENROLLED_FACTOR;
import static io.gravitee.am.factor.utils.SharedSecret.base32Str2Hex;
import static java.lang.System.currentTimeMillis;
import static java.util.Objects.nonNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OTPFactorProviderTest {

    @Mock
    private OTPFactorConfiguration otpFactorConfiguration;

    @InjectMocks
    public OTPFactorProvider otpFactorProvider = new OTPFactorProvider();

    private EnrolledFactor enrolledFactor;

    @Before
    public void setUp() {
        enrolledFactor = new EnrolledFactor();
        enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, SharedSecret.generate()));

        when(otpFactorConfiguration.getIssuer()).thenReturn("Gravitee.io AM");
    }

    @Test
    public void must_verify_correctly_otp() {
        var data = Map.of(
                "code", generateCode(enrolledFactor.getSecurity().getValue(), currentTimeMillis()),
                KEY_ENROLLED_FACTOR, enrolledFactor
        );
        var factorContext = new FactorContext(null, data);

        otpFactorProvider.verify(factorContext).test()
                .assertNoErrors()
                .assertComplete();
    }

    @Test
    public void must_verify_correctly_otp_window_two_attempts() {
        var data = Map.of(
                "code", generateCode(enrolledFactor.getSecurity().getValue(), currentTimeMillis() - TOTP.TIME_STEP),
                KEY_ENROLLED_FACTOR, enrolledFactor
        );
        var factorContext = new FactorContext(null, data);

        otpFactorProvider.verify(factorContext).test()
                .assertNoErrors()
                .assertComplete();
    }

    @Test
    public void must_verify_correctly_otp_window_three_attempts() {
        var data = Map.of(
                "code", generateCode(enrolledFactor.getSecurity().getValue(), (currentTimeMillis() - TOTP.TIME_STEP * 2) + 1000),
                KEY_ENROLLED_FACTOR, enrolledFactor
        );
        var factorContext = new FactorContext(null, data);

        otpFactorProvider.verify(factorContext).test()
                .assertNoErrors()
                .assertComplete();
    }

    @Test
    public void must_NOT_verify_correctly_otp_window_four_attempts() {
        var data = Map.of(
                "code", generateCode(enrolledFactor.getSecurity().getValue(), currentTimeMillis() - TOTP.TIME_STEP * 3),
                KEY_ENROLLED_FACTOR, enrolledFactor
        );
        var factorContext = new FactorContext(null, data);

        otpFactorProvider.verify(factorContext).test()
                .assertError(InvalidCodeException.class);
    }

    @Test
    public void must_enroll_otp() throws InterruptedException {
        var observer = otpFactorProvider.enroll("accountName").test();
        observer.await(10, TimeUnit.SECONDS);

        observer.assertNoErrors()
                .assertComplete()
                .assertValueCount(1)
                .assertValue(enrollement -> nonNull(enrollement.getKey()) &&
                        nonNull(enrollement.getBarCode()) && enrollement.getBarCode().startsWith("data:image/png;base64,")
                );
    }

    @Test
    public void must_generate_qr_code() throws InterruptedException {
        final User user = new User();
        user.setUsername("user");
        var observer = otpFactorProvider.generateQrCode(user, enrolledFactor).test();
        observer.await(10, TimeUnit.SECONDS);

        observer.assertNoErrors()
                .assertComplete()
                .assertValueCount(1)
                .assertValue(code -> code.startsWith("data:image/png;base64,"));
    }

    @Test
    public void must_check_security_factor() throws InterruptedException {
        assertTrue(otpFactorProvider.checkSecurityFactor(null));

        final EnrolledFactor factor = new EnrolledFactor();
        assertFalse(otpFactorProvider.checkSecurityFactor(factor));

        final EnrolledFactorSecurity security = new EnrolledFactorSecurity();
        factor.setSecurity(security);
        assertFalse(otpFactorProvider.checkSecurityFactor(factor));

        security.setValue("value");
        assertTrue(otpFactorProvider.checkSecurityFactor(factor));
    }

    private String generateCode(String value, long time) {
        return TOTP.generateTOTP(base32Str2Hex(value), time / TOTP.TIME_STEP);
    }
}
