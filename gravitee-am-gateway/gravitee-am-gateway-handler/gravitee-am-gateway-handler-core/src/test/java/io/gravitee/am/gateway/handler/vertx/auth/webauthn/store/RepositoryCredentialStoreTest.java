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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.store;

import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.CredentialService;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.ext.auth.webauthn.Authenticator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.intThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RepositoryCredentialStoreTest {

    @InjectMocks
    private RepositoryCredentialStore repositoryCredentialStore = new RepositoryCredentialStore();

    @Mock
    private CredentialService credentialService;

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private Domain domain;

    @Test
    public void shouldFetchAuthenticator_byUsername_emptyList() {
        repositoryCredentialStore.maxAllowCredentials = 5;

        Authenticator query = new Authenticator();
        query.setUserName("username");

        when(domain.getId()).thenReturn("domain-id");
        when(credentialService.findByUsername(eq(ReferenceType.DOMAIN), eq("domain-id"), eq(query.getUserName()), intThat(i -> i == 5))).thenReturn(Flowable.empty());
        when(jwtBuilder.sign(any())).thenReturn("part1.part2.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");

        List<Authenticator> authenticators = repositoryCredentialStore.fetch(query).blockingGet();
        List<Authenticator> authenticators2 = repositoryCredentialStore.fetch(query).blockingGet();

        Assert.assertNotNull(authenticators);
        Assert.assertNotNull(authenticators2);
        Assert.assertTrue(!authenticators.isEmpty());
        Assert.assertTrue(!authenticators2.isEmpty());
        Assert.assertTrue(authenticators.get(0).getCredID().equals(authenticators2.get(0).getCredID()));
        verify(jwtBuilder, times(4)).sign(any());
    }

    @Test
    public void shouldFetchAuthenticator_byUsername_emptyList_different_users() {
        repositoryCredentialStore.maxAllowCredentials = 5;

        Authenticator query = new Authenticator();
        query.setUserName("username");

        Authenticator query2 = new Authenticator();
        query2.setUserName("username2");

        when(domain.getId()).thenReturn("domain-id");
        when(credentialService.findByUsername(eq(ReferenceType.DOMAIN), eq("domain-id"), eq(query.getUserName()), anyInt())).thenReturn(Flowable.empty());
        when(credentialService.findByUsername(eq(ReferenceType.DOMAIN), eq("domain-id"), eq(query2.getUserName()), anyInt())).thenReturn(Flowable.empty());
        when(jwtBuilder.sign(any())).thenReturn("part1.part2.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c").thenReturn("part1.part2.-sVkXqTOhFeJwQXyH3WhuNJfAfnRkVM6llEu6k46iqY");

        List<Authenticator> authenticators = repositoryCredentialStore.fetch(query).blockingGet();
        List<Authenticator> authenticators2 = repositoryCredentialStore.fetch(query2).blockingGet();

        Assert.assertNotNull(authenticators);
        Assert.assertNotNull(authenticators2);
        Assert.assertFalse(authenticators.isEmpty());
        Assert.assertFalse(authenticators2.isEmpty());
        Assert.assertFalse(authenticators.get(0).getCredID().equals(authenticators2.get(0).getCredID()));
        verify(jwtBuilder, times(4)).sign(any());
    }

    @Test
    public void shouldFetchAuthenticator_byUsername_withValues() {
        repositoryCredentialStore.maxAllowCredentials = 5;

        Authenticator query = new Authenticator();
        query.setUserName("username");

        Credential credential = new Credential();
        credential.setUsername(query.getUserName());
        credential.setCredentialId("credID");

        when(domain.getId()).thenReturn("domain-id");
        when(credentialService.findByUsername(eq(ReferenceType.DOMAIN), eq("domain-id"), eq(query.getUserName()), anyInt())).thenReturn(Flowable.just(credential));
        List<Authenticator> authenticators = repositoryCredentialStore.fetch(query).blockingGet();

        Assert.assertNotNull(authenticators);
        Assert.assertEquals(1, authenticators.size());
        Assert.assertEquals("credID", authenticators.get(0).getCredID());
        Assert.assertEquals(authenticators.get(0).getUserName(), query.getUserName());
        verify(jwtBuilder, never()).sign(any());
    }

    @Test
    public void shouldFetchAllValuesWhenMaxAllowCredentialsLessLEQ0() {
        repositoryCredentialStore.maxAllowCredentials = -1;

        Authenticator query = new Authenticator();
        query.setUserName("username");

        Credential credential = new Credential();
        credential.setUsername(query.getUserName());
        credential.setCredentialId("credID");

        when(domain.getId()).thenReturn("domain-id");
        when(credentialService.findByUsername(eq(ReferenceType.DOMAIN), eq("domain-id"), eq(query.getUserName()))).thenReturn(Flowable.just(credential));
        List<Authenticator> authenticators = repositoryCredentialStore.fetch(query).blockingGet();

        Assert.assertNotNull(authenticators);
        Assert.assertEquals(1, authenticators.size());
        Assert.assertEquals("credID", authenticators.get(0).getCredID());
        Assert.assertEquals(authenticators.get(0).getUserName(), query.getUserName());
        verify(jwtBuilder, never()).sign(any());
    }
}
