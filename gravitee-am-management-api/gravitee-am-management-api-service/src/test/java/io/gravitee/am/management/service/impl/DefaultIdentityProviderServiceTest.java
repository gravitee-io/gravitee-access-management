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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoderOptions;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DefaultIdentityProviderServiceTest {

    @Mock
    private IdentityProviderService identityProviderService;

    @Spy
    private MockEnvironment environment = new MockEnvironment();

    @InjectMocks
    private DefaultIdentityProviderServiceImpl cut;

    @BeforeEach
    public void setup() {
        cut = new DefaultIdentityProviderServiceImpl(identityProviderService, environment);
    }

    @Test
    public void createDefaultMongoProvider() {

        String domainId = "domain";
        ArgumentCaptor<NewIdentityProvider> newIdentityProviderArgumentCaptor = ArgumentCaptor.forClass(NewIdentityProvider.class);

        when(identityProviderService.create(any(), eq(domainId), newIdentityProviderArgumentCaptor.capture(), isNull(), anyBoolean())).thenReturn(Single.just(new IdentityProvider()));
        TestObserver<IdentityProvider> observer = cut.create(domainId).test();
        observer.assertComplete();

        verify(identityProviderService).create(any(), eq(domainId), newIdentityProviderArgumentCaptor.capture(), isNull(), anyBoolean());
        NewIdentityProvider capturedIdp = newIdentityProviderArgumentCaptor.getValue();
        assertNotNull(capturedIdp);
        assertEquals("default-idp-domain", capturedIdp.getId());
        assertEquals("Default Identity Provider", capturedIdp.getName());
        assertNotNull(capturedIdp.getConfiguration());
    }

    @Test
    public void createDefaultJDBCProvider() {
        String domainId = "domain";
        environment.setProperty("management.type", "jdbc");
        ArgumentCaptor<NewIdentityProvider> newIdentityProviderArgumentCaptor = ArgumentCaptor.forClass(NewIdentityProvider.class);
        when(identityProviderService.create(any(), eq(domainId), newIdentityProviderArgumentCaptor.capture(), isNull(), anyBoolean())).thenReturn(Single.just(new IdentityProvider()));

        TestObserver<IdentityProvider> observer = cut.create(domainId).test();
        observer.assertComplete();

        verify(identityProviderService).create(any(), eq(domainId), newIdentityProviderArgumentCaptor.capture(), isNull(), anyBoolean());
        NewIdentityProvider capturedIdp = newIdentityProviderArgumentCaptor.getValue();
        assertNotNull(capturedIdp);
        assertEquals("default-idp-domain", capturedIdp.getId());
        assertEquals("Default Identity Provider", capturedIdp.getName());
        assertEquals("jdbc-am-idp", capturedIdp.getType());
        assertNotNull(capturedIdp.getConfiguration());
    }

    @Test
    public void shouldComputeShorterId() {
        String domainId = "domaindomaindomaindomaindomaindomaindomaindomaindomaindomaindomaindomaindomaindomaindomaindomaindomain";
        environment.setProperty("management.type", "jdbc");
        ArgumentCaptor<NewIdentityProvider> newIdentityProviderArgumentCaptor = ArgumentCaptor.forClass(NewIdentityProvider.class);
        when(identityProviderService.create(any(), eq(domainId), newIdentityProviderArgumentCaptor.capture(), isNull(), anyBoolean())).thenReturn(Single.just(new IdentityProvider()));

        TestObserver<IdentityProvider> observer = cut.create(domainId).test();
        observer.assertComplete();

        verify(identityProviderService).create(any(), eq(domainId), newIdentityProviderArgumentCaptor.capture(), isNull(), anyBoolean());
        NewIdentityProvider capturedIdp = newIdentityProviderArgumentCaptor.getValue();
        assertNotNull(capturedIdp);
        assertNotEquals("default-idp-domain", capturedIdp.getId());
        assertEquals("Default Identity Provider", capturedIdp.getName());
        assertEquals("jdbc-am-idp", capturedIdp.getType());
        assertNotNull(capturedIdp.getConfiguration());
    }


    @Test
    public void byDefault_DefaultIDPsPasswordAlgIsBCryptWith10IterationRounds() {
        Map<String, Object> test = cut.createProviderConfiguration("test", null);

        assertEquals("BCrypt", test.get("passwordEncoder"));
        assertEquals(10, ((PasswordEncoderOptions) test.get("passwordEncoderOptions")).getRounds());
    }

    @Test
    public void defaultIterationRoundsForBCryptIs10() {
        environment.setProperty("domains.identities.default.passwordEncoder.algorithm", "BCrypt");

        Map<String, Object> test = cut.createProviderConfiguration("test", null);

        assertEquals("BCrypt", test.get("passwordEncoder"));
        assertEquals(10, ((PasswordEncoderOptions) test.get("passwordEncoderOptions")).getRounds());
    }

    @Test
    public void shouldReturnUpdatedIterationRoundsForBCryptAlg() {
        environment.setProperty("domains.identities.default.passwordEncoder.algorithm", "BCrypt");
        environment.setProperty("domains.identities.default.passwordEncoder.properties.rounds", "11");

        Map<String, Object> test = cut.createProviderConfiguration("test", null);

        assertEquals("BCrypt", test.get("passwordEncoder"));
        assertEquals(11, ((PasswordEncoderOptions) test.get("passwordEncoderOptions")).getRounds());
    }

    @Test
    public void defaultIterationRoundsForShaIs1() {
        environment.setProperty("domains.identities.default.passwordEncoder.algorithm", "SHA-256");

        Map<String, Object> test = cut.createProviderConfiguration("test", null);

        assertEquals("SHA-256", test.get("passwordEncoder"));
        assertEquals(1, ((PasswordEncoderOptions) test.get("passwordEncoderOptions")).getRounds());
    }

    @Test
    public void shouldReturnUpdatedIterationRoundsForShaAlg() {
        environment.setProperty("domains.identities.default.passwordEncoder.algorithm", "SHA-384");
        environment.setProperty("domains.identities.default.passwordEncoder.properties.rounds", "11");

        Map<String, Object> test = cut.createProviderConfiguration("test", null);

        assertEquals("SHA-384", test.get("passwordEncoder"));
        assertEquals(11, ((PasswordEncoderOptions) test.get("passwordEncoderOptions")).getRounds());
    }
}