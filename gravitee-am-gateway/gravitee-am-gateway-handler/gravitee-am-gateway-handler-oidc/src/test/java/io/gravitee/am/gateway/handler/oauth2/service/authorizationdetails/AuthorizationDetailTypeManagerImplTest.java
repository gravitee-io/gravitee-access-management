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
package io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails;

import io.gravitee.am.common.event.AuthorizationDetailTypeEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.impl.AuthorizationDetailTypeManagerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.repository.management.api.AuthorizationDetailTypeRepository;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationDetailTypeManagerImplTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String PRELOAD_PLUGIN_ID = Type.AUTHORIZATION_DETAIL_TYPE.name();

    @InjectMocks
    private final AuthorizationDetailTypeManagerImpl manager = new AuthorizationDetailTypeManagerImpl();

    @Mock
    private Domain domain;
    @Mock
    private AuthorizationDetailTypeRepository authorizationDetailTypeRepository;
    @Mock
    private DomainReadinessService domainReadinessService;
    @Mock
    private EventManager eventManager;
    @Mock
    private Payload payload;
    @Mock
    private Event<AuthorizationDetailTypeEvent, Payload> event;

    @BeforeEach
    void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(domain.getName()).thenReturn("domain-name");
    }

    @Test
    void shouldLoadTheDomainTypesAndListenForChanges() {
        preload(type("adt-1", "payment_initiation", true));

        assertThat(manager.findByType("payment_initiation")).map(AuthorizationDetailType::getId).contains("adt-1");
        verify(domainReadinessService).pluginLoaded(DOMAIN_ID, "adt-1");
        verify(eventManager).subscribeForEvents(manager, AuthorizationDetailTypeEvent.class, DOMAIN_ID);
    }

    @Test
    void shouldMatchTypesCaseSensitively() {
        preload(type("adt-1", "payment_initiation", true));

        assertThat(manager.findByType("Payment_Initiation")).isEmpty();
        assertThat(manager.findByType(null)).isEmpty();
    }

    @Test
    void shouldListOnlyDiscoveryTypesSorted() {
        preload(type("adt-1", "payment_initiation", true),
                type("adt-2", "account_information", true),
                type("adt-3", "internal_transfer", false));

        assertThat(manager.findDiscoveryTypes()).containsExactly("account_information", "payment_initiation");
    }

    @Test
    void shouldAddACreatedType() {
        preload();
        when(authorizationDetailTypeRepository.findById("adt-1")).thenReturn(Maybe.just(type("adt-1", "payment_initiation", true)));
        stubEvent(AuthorizationDetailTypeEvent.DEPLOY, "adt-1", DOMAIN_ID);

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(manager.findByType("payment_initiation")).isPresent());
        verify(domainReadinessService).pluginLoaded(DOMAIN_ID, "adt-1");
    }

    @Test
    void shouldReplaceAnUpdatedType() {
        preload(type("adt-1", "payment_initiation", false));
        when(authorizationDetailTypeRepository.findById("adt-1")).thenReturn(Maybe.just(type("adt-1", "payment_initiation", true)));
        stubEvent(AuthorizationDetailTypeEvent.UPDATE, "adt-1", DOMAIN_ID);

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(manager.findDiscoveryTypes()).containsExactly("payment_initiation"));
    }

    @Test
    void shouldRemoveADeletedType() {
        preload(type("adt-1", "payment_initiation", true));
        stubEvent(AuthorizationDetailTypeEvent.UNDEPLOY, "adt-1", DOMAIN_ID);

        manager.onEvent(event);

        assertThat(manager.findByType("payment_initiation")).isEmpty();
        assertThat(manager.findDiscoveryTypes()).isEmpty();
        verify(domainReadinessService).pluginRemoved(DOMAIN_ID, "adt-1");
    }

    @Test
    void shouldRemoveATypeThatNoLongerExistsOnUpdate() {
        preload(type("adt-1", "payment_initiation", true));
        when(authorizationDetailTypeRepository.findById("adt-1")).thenReturn(Maybe.empty());
        stubEvent(AuthorizationDetailTypeEvent.UPDATE, "adt-1", DOMAIN_ID);

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(manager.findByType("payment_initiation")).isEmpty());
    }

    @Test
    void shouldIgnoreEventsOfAnotherDomain() {
        preload();
        stubEvent(AuthorizationDetailTypeEvent.DEPLOY, "adt-1", "domain-2");

        manager.onEvent(event);

        verify(authorizationDetailTypeRepository, never()).findById(anyString());
    }

    private void preload(AuthorizationDetailType... types) {
        when(authorizationDetailTypeRepository.findByDomain(DOMAIN_ID)).thenReturn(Flowable.fromArray(types));
        manager.afterPropertiesSet();
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(domainReadinessService).pluginLoaded(DOMAIN_ID, PRELOAD_PLUGIN_ID));
    }

    private void stubEvent(AuthorizationDetailTypeEvent type, String id, String referenceId) {
        when(event.content()).thenReturn(payload);
        when(event.type()).thenReturn(type);
        when(payload.getId()).thenReturn(id);
        when(payload.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(payload.getReferenceId()).thenReturn(referenceId);
    }

    private static AuthorizationDetailType type(String id, String type, boolean discovery) {
        return AuthorizationDetailType.builder().id(id).domain(DOMAIN_ID).type(type).discovery(discovery).build();
    }
}
