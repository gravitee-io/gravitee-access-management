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
package io.gravitee.am.factor.recovery.code.provider;

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.recovery.code.RecoveryCodeFactorConfiguration;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RecoveryCodeFactorProviderTest {

    @InjectMocks
    private RecoveryCodeFactorProvider recoveryCodeFactorProvider;

    @Mock
    private RecoveryCodeFactorConfiguration configuration;

    @Mock
    private FactorContext factorContext;

    @Mock
    private UserService userService;

    private EnrolledFactor enrolledFactor;
    private Factor factor;

    @Before
    public void init() {
        when(factorContext.getComponent(UserService.class)).thenReturn(userService);

        User user = mock(User.class);
        when(user.getId()).thenReturn("any id");
        when(factorContext.getUser()).thenReturn(user);
        when(userService.addFactor(any(), any(), any())).thenReturn(Single.just(user));

        enrolledFactor = new EnrolledFactor();
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        factor = new Factor();
        factor.setId("anyId");
        when(factorContext.getData(FactorContext.KEY_RECOVERY_FACTOR, Factor.class)).thenReturn(factor);
    }

    @Test
    public void generateRecoveryCodeShouldPersist() {
        int anyDigit = 6;
        when(configuration.getDigit()).thenReturn(anyDigit);
        when(configuration.getCount()).thenReturn(anyDigit);

        TestObserver<EnrolledFactorSecurity> test = recoveryCodeFactorProvider.generateRecoveryCode(factorContext).test();
        test.awaitTerminalEvent();

        test.assertNoErrors();
        EnrolledFactorSecurity security = test.values().get(0);
        assertThat(security.getType(), is(RECOVERY_CODE));
        assertThat(security.getAdditionalData(), notNullValue());
        verify(userService).addFactor(any(), any(), any());
    }

    @Test
    public void shouldVerifyRecoveryCode() {
        enrolledFactor.setSecurity(createFactorSecurityWithRecoveryCode());
        final String code = "three";
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(code);

        TestObserver<Void> test = recoveryCodeFactorProvider.verify(factorContext).test();
        test.awaitTerminalEvent();

        test.assertNoValues();
        test.assertNoErrors();
    }

    @Test
    public void verifyShouldThrowExceptionForBadRecoveryCode() {
        enrolledFactor.setSecurity(createFactorSecurityWithRecoveryCode());
        final String invalidCode = "Invalid Code";
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(invalidCode);

        TestObserver<Void> test = recoveryCodeFactorProvider.verify(factorContext).test();
        test.awaitTerminalEvent();

        test.assertNoValues();
        test.assertError(InvalidCodeException.class);
    }

    private EnrolledFactorSecurity createFactorSecurityWithRecoveryCode(){
        return new EnrolledFactorSecurity(RECOVERY_CODE, Integer.toString(configuration.getDigit()),
                Map.of(RECOVERY_CODE, new ArrayList<>(Arrays.asList("one", "two", "three"))));
    }
}