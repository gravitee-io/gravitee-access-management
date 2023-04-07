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
package io.gravitee.am.factor.sms.provider;

import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.sms.SMSFactorConfiguration;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.MessageResourceProvider;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.el.TemplateEngine;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SMSFactorProviderTest {

    private static final String CODE = "886024";
    private static final String SHARED_SECRET = "RVVAGS23PDJNU3NPRBMUHFXU4OXZTNRA";

    @InjectMocks
    private SMSFactorProvider provider;

    @Mock
    private SMSFactorConfiguration configuration;

    @Test
    public void shouldValidatePhoneNumber() {
        when(configuration.countries()).thenReturn(Arrays.asList("fr"));
        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "+33612345678"));
        assertTrue(provider.checkSecurityFactor(factor));
    }

    @Test
    public void shouldNotBeValidPhoneNumber_WrongCountry() {
        when(configuration.countries()).thenReturn(Arrays.asList("US", "GB"));
        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "+33612345678"));
        assertFalse(provider.checkSecurityFactor(factor));
    }

    @Test
    public void shouldValidatePhoneNumber_MultipleCountries() {
        when(configuration.countries()).thenReturn(Arrays.asList("US", "FR", "GB"));
        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "+33612345678"));
        assertTrue(provider.checkSecurityFactor(factor));
    }

    @Test
    public void shouldVerify_nominalCase_mfaResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MFAResourceProvider mfaResourceProvider = mock(MFAResourceProvider.class);
        when(mfaResourceProvider.verify(any())).thenReturn(Completable.complete());
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(mfaResourceProvider);
        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        EnrolledFactorChannel enrolledFactorChannel = mock(EnrolledFactorChannel.class);
        when(enrolledFactorChannel.getTarget()).thenReturn("target");
        when(enrolledFactor.getChannel()).thenReturn(enrolledFactorChannel);
        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn("code");
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        TestObserver testObserver = provider.verify(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldVerify_nominalCase_messageResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        when(configuration.getReturnDigits()).thenReturn(6);
        ResourceManager resourceManager = mock(ResourceManager.class);
        MessageResourceProvider messageResourceProvider = mock(MessageResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(messageResourceProvider);
        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        EnrolledFactorSecurity enrolledFactorSecurity = mock(EnrolledFactorSecurity.class);
        when(enrolledFactorSecurity.getValue()).thenReturn(SHARED_SECRET);
        when(enrolledFactorSecurity.getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class)).thenReturn(0);
        when(enrolledFactorSecurity.getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class)).thenReturn(Instant.now().plusSeconds(60).toEpochMilli());
        when(enrolledFactor.getSecurity()).thenReturn(enrolledFactorSecurity);
        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        TestObserver testObserver = provider.verify(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldNotVerify_wrongResourceType() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        ResourceProvider genericResourceProvider = mock(ResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(genericResourceProvider);

        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getData(FactorContext.KEY_CODE, String.class)).thenReturn(CODE);
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        TestObserver testObserver = provider.verify(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNeedChallengeSending() {
        Assert.assertTrue(provider.needChallengeSending());
    }

    @Test
    public void shouldNotUseVariableFactorSecurity_mfaResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MFAResourceProvider mfaResourceProvider = mock(MFAResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(mfaResourceProvider);

        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        Assert.assertFalse(provider.useVariableFactorSecurity(factorContext));
    }

    @Test
    public void shouldUseVariableFactorSecurity_messageResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MessageResourceProvider messageResourceProvider = mock(MessageResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(messageResourceProvider);

        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        Assert.assertTrue(provider.useVariableFactorSecurity(factorContext));
    }

    @Test
    public void shouldEnroll_mfaResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MFAResourceProvider mfaResourceProvider = mock(MFAResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(mfaResourceProvider);

        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        TestObserver<Enrollment> testObserver = provider.enroll(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(enrollment -> enrollment.getKey() == null);
    }

    @Test
    public void shouldEnroll_messageResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MessageResourceProvider messageResourceProvider = mock(MessageResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(messageResourceProvider);

        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        TestObserver<Enrollment> testObserver = provider.enroll(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(enrollment -> enrollment.getKey() != null);
    }

    @Test
    public void shouldSendChallenge_nominalCase_mfaResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MFAResourceProvider mfaResourceProvider = mock(MFAResourceProvider.class);
        when(mfaResourceProvider.send(any())).thenReturn(Completable.complete());
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(mfaResourceProvider);

        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        EnrolledFactorChannel enrolledFactorChannel = mock(EnrolledFactorChannel.class);
        when(enrolledFactorChannel.getTarget()).thenReturn("target");
        when(enrolledFactor.getChannel()).thenReturn(enrolledFactorChannel);
        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        TestObserver testObserver = provider.sendChallenge(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldSendChallenge_nominalCase_messageResource() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        when(configuration.getMessageBody()).thenReturn("message");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MessageResourceProvider messageResourceProvider = mock(MessageResourceProvider.class);
        when(messageResourceProvider.sendMessage(any())).thenReturn(Completable.complete());
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(messageResourceProvider);

        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        EnrolledFactorChannel enrolledFactorChannel = mock(EnrolledFactorChannel.class);
        EnrolledFactorSecurity enrolledFactorSecurity = mock(EnrolledFactorSecurity.class);
        when(enrolledFactorSecurity.getValue()).thenReturn(SHARED_SECRET);
        when(enrolledFactorSecurity.getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class)).thenReturn(0);
        when(enrolledFactor.getSecurity()).thenReturn(enrolledFactorSecurity);
        when(enrolledFactorChannel.getTarget()).thenReturn("target");
        when(enrolledFactor.getChannel()).thenReturn(enrolledFactorChannel);
        FactorContext factorContext = mock(FactorContext.class);
        TemplateEngine templateEngine = mock(TemplateEngine.class);
        UserService userService = mock(UserService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        when(userService.addFactor(anyString(), any(), any())).thenReturn(Single.just(user));
        when(templateEngine.getValue(anyString(), eq(String.class))).thenReturn("message");
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getUser()).thenReturn(user);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        when(factorContext.getTemplateEngine()).thenReturn(templateEngine);
        when(factorContext.getComponent(UserService.class)).thenReturn(userService);

        TestObserver testObserver = provider.sendChallenge(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldSendChallenge_nominalCase_messageResource_incrementMovingFactor() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        when(configuration.getMessageBody()).thenReturn("message");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MessageResourceProvider messageResourceProvider = mock(MessageResourceProvider.class);
        when(messageResourceProvider.sendMessage(any())).thenReturn(Completable.complete());
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(messageResourceProvider);

        EnrolledFactor enrolledFactor = new EnrolledFactor();
        EnrolledFactorChannel enrolledFactorChannel = new EnrolledFactorChannel();
        EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.setValue(SHARED_SECRET);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, 0);
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_EXPIRE_AT, Instant.now().minusSeconds(60).toEpochMilli());
        enrolledFactor.setSecurity(enrolledFactorSecurity);
        enrolledFactorChannel.setTarget("target");
        enrolledFactor.setChannel(enrolledFactorChannel);
        FactorContext factorContext = mock(FactorContext.class);
        TemplateEngine templateEngine = mock(TemplateEngine.class);
        UserService userService = mock(UserService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        ArgumentCaptor<EnrolledFactor> enrolledFactorCaptor = ArgumentCaptor.forClass(EnrolledFactor.class);
        when(userService.addFactor(anyString(), enrolledFactorCaptor.capture(), any())).thenReturn(Single.just(user));
        when(templateEngine.getValue(anyString(), eq(String.class))).thenReturn("message");
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getUser()).thenReturn(user);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        when(factorContext.getTemplateEngine()).thenReturn(templateEngine);
        when(factorContext.getComponent(UserService.class)).thenReturn(userService);

        TestObserver testObserver = provider.sendChallenge(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        EnrolledFactor enrolledFactorCaptorValue = enrolledFactorCaptor.getValue();
        assertNotNull(enrolledFactorCaptorValue);
        assertTrue(enrolledFactorCaptorValue.getSecurity().getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class).longValue() == 1l);
    }

    @Test
    public void shouldNotSendChallenge_messageResource_wrongSecret() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MessageResourceProvider messageResourceProvider = mock(MessageResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(messageResourceProvider);

        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        EnrolledFactorSecurity enrolledFactorSecurity = mock(EnrolledFactorSecurity.class);
        when(enrolledFactorSecurity.getValue()).thenReturn("wrong-secret");
        when(enrolledFactor.getSecurity()).thenReturn(enrolledFactorSecurity);
        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        TestObserver testObserver = provider.sendChallenge(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotSendChallenge_messageResource_templateError() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        when(configuration.getMessageBody()).thenReturn("message");
        ResourceManager resourceManager = mock(ResourceManager.class);
        MessageResourceProvider messageResourceProvider = mock(MessageResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(messageResourceProvider);

        EnrolledFactor enrolledFactor = mock(EnrolledFactor.class);
        EnrolledFactorChannel enrolledFactorChannel = mock(EnrolledFactorChannel.class);
        EnrolledFactorSecurity enrolledFactorSecurity = mock(EnrolledFactorSecurity.class);
        when(enrolledFactorSecurity.getValue()).thenReturn(SHARED_SECRET);
        when(enrolledFactorSecurity.getData(FactorDataKeys.KEY_MOVING_FACTOR, Number.class)).thenReturn(0);
        when(enrolledFactor.getSecurity()).thenReturn(enrolledFactorSecurity);
        when(enrolledFactorChannel.getTarget()).thenReturn("target");
        when(enrolledFactor.getChannel()).thenReturn(enrolledFactorChannel);
        FactorContext factorContext = mock(FactorContext.class);
        TemplateEngine templateEngine = mock(TemplateEngine.class);
        when(templateEngine.getValue(anyString(), eq(String.class))).thenThrow(IllegalArgumentException.class);
        when(factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class)).thenReturn(enrolledFactor);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        when(factorContext.getTemplateEngine()).thenReturn(templateEngine);

        TestObserver testObserver = provider.sendChallenge(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotSendChallenge_wrongResourceType() {
        when(configuration.getGraviteeResource()).thenReturn("resource-id");
        ResourceManager resourceManager = mock(ResourceManager.class);
        ResourceProvider genericResourceProvider = mock(ResourceProvider.class);
        when(resourceManager.getResourceProvider("resource-id")).thenReturn(genericResourceProvider);

        FactorContext factorContext = mock(FactorContext.class);
        when(factorContext.getComponent(ResourceManager.class)).thenReturn(resourceManager);

        TestObserver testObserver = provider.sendChallenge(factorContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalException.class);
        testObserver.assertNotComplete();
    }

}
