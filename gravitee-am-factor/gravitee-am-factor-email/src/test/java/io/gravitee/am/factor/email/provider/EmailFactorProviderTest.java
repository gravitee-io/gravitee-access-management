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
package io.gravitee.am.factor.email.provider;

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.email.EmailFactorConfiguration;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.resource.api.email.EmailSenderProvider;
import io.gravitee.common.util.Maps;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private EmailService emailService;
    @Mock
    private UserService userService;

    @Before
    public void init() {
        reset(configuration, resourceManager, factorContext, emailService, userService);

        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        when(factorContext.getComponent(EmailService.class)).thenReturn(emailService);
        when(factorContext.getComponent(UserService.class)).thenReturn(userService);
    }

    @Test
    public void shouldSendEmailAndGenerateCode() throws Exception{
        EmailSenderProvider smtpProvider = mock(EmailSenderProvider.class);
        when(resourceManager.getResourceProvider(any())).thenReturn(smtpProvider);

        Email template = new Email();
        template.setTemplate("mfa_challenge.html");
        template.setSubject("Some Subject");
        template.setExpiresAfter(600);

        io.gravitee.am.common.email.Email generatedEmail = new io.gravitee.am.common.email.Email();
        generatedEmail.setTo(new String[]{RECIPIENT});
        when(emailService.createEmail(any(), any(), any(), any(), any())).thenReturn(new EmailService.EmailWrapper(generatedEmail));

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                .put(FactorDataKeys.KEY_MOVING_FACTOR, 0)
                .put(FactorDataKeys.KEY_EXPIRE_AT, System.currentTimeMillis() + 600)
                .build();
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, additionalData));
        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getTemplateValues()).thenReturn(new HashMap<>());
        User user = mock(User.class);
        when(user.getId()).thenReturn("id");
        when(factorContext.getUser()).thenReturn(user);

        when(userService.addFactor(any(), any(), any())).thenReturn(Single.just(user));
        when(smtpProvider.sendMessage(any(), anyBoolean())).thenReturn(Completable.complete());

        TestObserver<Void> test = cut.sendChallenge(factorContext).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoValues();
        test.assertNoErrors();

        verify(smtpProvider).sendMessage(argThat(m -> m.getTo()[0].equals(RECIPIENT)), anyBoolean());
        verify(userService).addFactor(any(), any(), any());
    }

    @Test
    public void shouldVerifyCode() {
        when(configuration.getReturnDigits()).thenReturn(6);

        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                .put(FactorDataKeys.KEY_MOVING_FACTOR, 0)
                .put(FactorDataKeys.KEY_EXPIRE_AT, System.currentTimeMillis() + 600)
                .build();
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, additionalData));
        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);

        TestObserver<Void> test = cut.verify(factorContext).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoValues();
        test.assertNoErrors();
    }

    @Test
    public void shouldNotVerifyCode_Expire() {
        when(configuration.getReturnDigits()).thenReturn(6);

        EnrolledFactor enrolled = new EnrolledFactor();
        Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                .put(FactorDataKeys.KEY_MOVING_FACTOR, 0)
                .put(FactorDataKeys.KEY_EXPIRE_AT, System.currentTimeMillis() - 600)
                .build();
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, additionalData));
        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);

        TestObserver<Void> test = cut.verify(factorContext).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoValues();
        test.assertError(InvalidCodeException.class);
    }

    @Test
    public void shouldNotVerifyCode_UnknownCode() {
        EnrolledFactor enrolled = new EnrolledFactor();
        enrolled.setUpdatedAt(new Date());
        Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                .put(FactorDataKeys.KEY_MOVING_FACTOR, 0)
                .put(FactorDataKeys.KEY_EXPIRE_AT, System.currentTimeMillis() + 600)
                .build();
        enrolled.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, SHARED_SECRET, additionalData));
        enrolled.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, RECIPIENT));
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolled);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);

        TestObserver<Void> test = cut.verify(factorContext).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoValues();
        test.assertError(InvalidCodeException.class);
    }

}
