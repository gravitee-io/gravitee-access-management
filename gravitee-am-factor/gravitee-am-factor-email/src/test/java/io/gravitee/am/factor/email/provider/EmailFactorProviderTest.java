package io.gravitee.am.factor.email.provider; /**
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

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.email.EmailFactorConfiguration;
import io.gravitee.am.gateway.handler.resource.ResourceManager;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.email.EmailSenderProvider;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.el.TemplateEngine;
import io.reactivex.Completable;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EmailFactorProviderTest {
    public static final String RECIPIENT = "myemail@domain.fr";
    public static final String CODE = "886024";
    public static final String SHARED_SECRET = "RVVAGS23PDJNU3NPRBMUHFXU4OXZTNRA";

    @InjectMocks
    private EmailFactorProvider cut;

    @Mock
    private EmailFactorConfiguration configuration;

    @Mock
    private ResourceManager resourceManager;

    @Mock
    private FactorContext factorContext;

    @Mock
    private TemplateEngine templateEngine;

    @Before
    public void init() throws Exception {
        this.cut.afterPropertiesSet();
        reset(configuration, resourceManager, factorContext);

        when(configuration.getTtl()).thenReturn(10);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        when(factorContext.getTemplateEngine()).thenReturn(templateEngine);
    }

    @Test
    public void shouldSendEmailAndGenerateCode() {
        when(configuration.isMfaResource()).thenReturn(false);
        EmailSenderProvider smtpProvider = mock(EmailSenderProvider.class);
        when(resourceManager.getResourceProvider(any())).thenReturn(smtpProvider);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, 0)));
        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);

        when(smtpProvider.sendMessage(any())).thenReturn(Completable.complete());

        TestObserver<Void> test = cut.sendChallenge(factorContext).test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertNoErrors();

        verify(smtpProvider).sendMessage(argThat(m -> m.getRecipient().equals(RECIPIENT)));
    }

    @Test
    public void shouldSendEmailWithoutGenerateCode() {
        when(configuration.isMfaResource()).thenReturn(true);

        MFAResourceProvider mfaProvider = mock(MFAResourceProvider.class);
        when(mfaProvider.send(any())).thenReturn(Completable.complete());

        when(resourceManager.getResourceProvider(any())).thenReturn(mfaProvider);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, 0)));        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);

        TestObserver<Void> test = cut.sendChallenge(factorContext).test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertNoErrors();

        verify(mfaProvider).send(argThat(ml -> ml.getTarget().equals(RECIPIENT)));
    }


    @Test
    public void shouldNotSendEmail_WrongResource() {
        when(configuration.isMfaResource()).thenReturn(true); // require MFAResource
        EmailSenderProvider smtpProvider = mock(EmailSenderProvider.class);
        when(resourceManager.getResourceProvider(any())).thenReturn(smtpProvider);

        when(resourceManager.getResourceProvider(any())).thenReturn(smtpProvider);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, 0)));        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);

        TestObserver<Void> test = cut.sendChallenge(factorContext).test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertError(TechnicalException.class);
    }

    @Test
    public void shouldVerifyCode() {
        when(configuration.isMfaResource()).thenReturn(false);
        when(configuration.getReturnDigits()).thenReturn(6);
        EmailSenderProvider smtpProvider = mock(EmailSenderProvider.class);
        when(resourceManager.getResourceProvider(any())).thenReturn(smtpProvider);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, 0)));        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);

        TestObserver<Void> test = cut.verify(factorContext).test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertNoErrors();
    }

    @Test
    public void shouldNotVerifyCode_Expire() {
        when(configuration.isMfaResource()).thenReturn(false);
        when(configuration.getReturnDigits()).thenReturn(6);
        EmailSenderProvider smtpProvider = mock(EmailSenderProvider.class);
        when(resourceManager.getResourceProvider(any())).thenReturn(smtpProvider);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date(Instant.now().minus(11, ChronoUnit.MINUTES).toEpochMilli()));
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, 0)));        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);

        TestObserver<Void> test = cut.verify(factorContext).test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertError(InvalidCodeException.class);
    }


    @Test
    public void shouldNotVerifyCode_UnknownCode() {
        when(configuration.isMfaResource()).thenReturn(false);
        EmailSenderProvider smtpProvider = mock(EmailSenderProvider.class);
        when(resourceManager.getResourceProvider(any())).thenReturn(smtpProvider);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, 0)));        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);

        TestObserver<Void> test = cut.verify(factorContext).test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertError(InvalidCodeException.class);
    }


    @Test
    public void shouldVerifyCode_MFA() {
        when(configuration.isMfaResource()).thenReturn(true);
        MFAResourceProvider mfaProvider = mock(MFAResourceProvider.class);
        when(resourceManager.getResourceProvider(any())).thenReturn(mfaProvider);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, 0)));        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);

        when(mfaProvider.verify(any())).thenReturn(Completable.complete());

        TestObserver<Void> test = cut.verify(factorContext).test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertNoErrors();

        verify(mfaProvider).verify(argThat(challenge -> challenge.getCode().equals(CODE) && challenge.getTarget().equals(RECIPIENT)));
    }
}
