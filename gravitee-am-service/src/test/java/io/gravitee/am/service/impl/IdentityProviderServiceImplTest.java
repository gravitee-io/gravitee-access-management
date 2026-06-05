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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.am.service.validators.idp.DatasourceValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class IdentityProviderServiceImplTest {

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PluginConfigurationValidationService validationService;

    @Mock
    private DatasourceValidator datasourceValidator;

    @InjectMocks
    private IdentityProviderServiceImpl service;

    private static final String IDP_ID = "idp-id";
    private static final String DOMAIN_ID = "domainId";

    private IdentityProvider existing(String type) {
        IdentityProvider idp = new IdentityProvider();
        idp.setId(IDP_ID);
        idp.setType(type);
        idp.setConfiguration("{}");
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId(DOMAIN_ID);
        return idp;
    }

    @Test
    void update_rejects_type_change() {
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(IDP_ID)))
                .thenReturn(Maybe.just(existing("inline-am-idp")));

        UpdateIdentityProvider update = new UpdateIdentityProvider();
        update.setName("name");
        update.setType("ldap-am-idp");
        update.setConfiguration("{}");

        TestObserver<IdentityProvider> observer =
                service.update(ReferenceType.DOMAIN, DOMAIN_ID, IDP_ID, update, new DefaultUser(), false).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);
        verify(identityProviderRepository, never()).update(any());
    }

    @Test
    void update_allows_matching_type() {
        when(identityProviderRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(IDP_ID)))
                .thenReturn(Maybe.just(existing("inline-am-idp")));
        when(datasourceValidator.validate(any())).thenReturn(Completable.complete());
        when(identityProviderRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        UpdateIdentityProvider update = new UpdateIdentityProvider();
        update.setName("name");
        update.setType("inline-am-idp");
        update.setConfiguration("{}");

        TestObserver<IdentityProvider> observer =
                service.update(ReferenceType.DOMAIN, DOMAIN_ID, IDP_ID, update, new DefaultUser(), false).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        verify(identityProviderRepository).update(any());
    }
}
