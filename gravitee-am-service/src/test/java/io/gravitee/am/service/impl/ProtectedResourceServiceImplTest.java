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
package io.gravitee.am.service.impl;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.ClientAlreadyExistsException;
import io.gravitee.am.service.exception.InvalidProtectedResourceException;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class ProtectedResourceServiceImplTest {

    @Mock
    private ProtectedResourceRepository repository;

    @Mock
    private ApplicationSecretConfig applicationSecretConfig;

    @Mock
    private SecretService secretService;

    @Mock
    private RoleService roleService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private OAuthClientUniquenessValidator oAuthClientUniquenessValidator;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ProtectedResourceServiceImpl service;


    @Test
    public void shouldNotCreateProtectedResourceWhenClientIdAlreadyExists() {
        Mockito.when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        Mockito.when(repository.create(any())).thenReturn(Single.never());
        Mockito.when(oAuthClientUniquenessValidator.checkClientIdUniqueness("domainId", "clientId"))
                .thenReturn(Completable.error(new ClientAlreadyExistsException("","")));
        Mockito.when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(false));
        Mockito.when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setClientId("clientId");
        newProtectedResource.setType("MCP_SERVER");
        newProtectedResource.setResourceIdentifiers(List.of("https://onet.pl"));
        service.create(domain, user, newProtectedResource)
                .test()
                .assertError(throwable -> throwable instanceof ClientAlreadyExistsException);

    }

    @Test
    public void shouldNotCreateProtectedResourceWhenResourceIdAlreadyExists() {
        Mockito.when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        Mockito.when(repository.create(any())).thenReturn(Single.never());
        Mockito.when(oAuthClientUniquenessValidator.checkClientIdUniqueness("domainId", "clientId"))
                .thenReturn(Completable.complete());
        Mockito.when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(true));
        Mockito.when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setClientId("clientId");
        newProtectedResource.setType("MCP_SERVER");
        newProtectedResource.setResourceIdentifiers(List.of("https://onet.pl"));
        service.create(domain, user, newProtectedResource)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException);

    }

    @Test
    public void shouldCreateProtectedResourceWhenClientIdDoesntExist() {
        Mockito.when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        Mockito.when(repository.create(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        Mockito.when(oAuthClientUniquenessValidator.checkClientIdUniqueness("domainId", "clientId"))
                .thenReturn(Completable.complete());
        Mockito.when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(false));
        Mockito.when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setClientId("clientId");
        newProtectedResource.setType("MCP_SERVER");
        newProtectedResource.setResourceIdentifiers(List.of("https://onet.pl"));
        service.create(domain, user, newProtectedResource)
                .test()
                .assertComplete()
                .assertValue(v -> v.getClientId().equals("clientId"));
        Mockito.verify(auditService, Mockito.times(1)).report(any());
    }


}