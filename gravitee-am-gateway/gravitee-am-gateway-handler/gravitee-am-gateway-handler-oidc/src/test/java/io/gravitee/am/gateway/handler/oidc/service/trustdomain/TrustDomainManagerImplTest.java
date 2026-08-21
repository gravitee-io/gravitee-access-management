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
package io.gravitee.am.gateway.handler.oidc.service.trustdomain;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.TrustDomainEvent;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.TrustDomainKeyService;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.impl.TrustDomainManagerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.model.oidc.TrustDomainTokenExchangeSettings;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustDomainManagerImplTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String PRELOAD_PLUGIN_ID = Type.TRUST_DOMAIN.name();

    @InjectMocks
    private final TrustDomainManagerImpl manager = new TrustDomainManagerImpl();

    @Mock
    private Domain domain;
    @Mock
    private TrustDomainRepository trustDomainRepository;
    @Mock
    private TrustDomainKeyService trustDomainKeyService;
    @Mock
    private DomainReadinessService domainReadinessService;
    @Mock
    private EventManager eventManager;
    @Mock
    private Payload payload;
    @Mock
    private Event<TrustDomainEvent, Payload> event;

    @BeforeEach
    void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(domain.getName()).thenReturn("domain-name");
    }

    private void stubEvent(TrustDomainEvent type, String trustDomainId) {
        stubEvent(type, trustDomainId, ReferenceType.DOMAIN, DOMAIN_ID);
    }

    private void stubEvent(TrustDomainEvent type, String trustDomainId, ReferenceType referenceType, String referenceId) {
        when(event.content()).thenReturn(payload);
        when(event.type()).thenReturn(type);
        when(payload.getId()).thenReturn(trustDomainId);
        when(payload.getReferenceType()).thenReturn(referenceType);
        when(payload.getReferenceId()).thenReturn(referenceId);
    }

    private static TrustDomain spiffe(String id, String name) {
        return TrustDomain.builder().id(id).name(name).kind(TrustDomainKind.SPIFFE).build();
    }

    private static TrustDomain tokenExchange(String id, String name, String issuer) {
        return TrustDomain.builder()
                .id(id)
                .name(name)
                .kind(TrustDomainKind.TOKEN_EXCHANGE)
                .tokenExchange(TrustDomainTokenExchangeSettings.builder().issuer(issuer).build())
                .build();
    }

    private void preload(TrustDomain... trustDomains) {
        when(trustDomainRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Flowable.fromArray(trustDomains));
        manager.afterPropertiesSet();
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(domainReadinessService).pluginLoaded(DOMAIN_ID, PRELOAD_PLUGIN_ID));
    }

    @Test
    void shouldIndexEachKindUnderItsOwnLookup() {
        preload(spiffe("td-1", "am.local"), tokenExchange("td-2", "issuer-name", "https://issuer.example.com"));

        assertThat(manager.findSpiffeByName("am.local").orElseThrow().getId()).isEqualTo("td-1");
        assertThat(manager.findByIssuer("https://issuer.example.com").orElseThrow().getId()).isEqualTo("td-2");
    }

    @Test
    void shouldNotResolveTokenExchangeTrustedDomainByName() {
        preload(tokenExchange("td-2", "am.local", "https://issuer.example.com"));

        assertThat(manager.findSpiffeByName("am.local")).isEmpty();
    }

    @Test
    void shouldNotResolveSpiffeTrustedDomainByIssuer() {
        preload(spiffe("td-1", "https://issuer.example.com"));

        assertThat(manager.findByIssuer("https://issuer.example.com")).isEmpty();
    }

    @Test
    void shouldReportInitialisationFailureWhenLoadFails() {
        when(trustDomainRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_ID))
                .thenReturn(Flowable.error(new IllegalStateException("database down")));

        manager.afterPropertiesSet();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(domainReadinessService).pluginInitFailed(DOMAIN_ID, PRELOAD_PLUGIN_ID, "database down"));
        verify(domainReadinessService, never()).pluginLoaded(DOMAIN_ID, PRELOAD_PLUGIN_ID);
    }

    @Test
    void shouldIgnoreEventForAnotherSecurityDomain() {
        preload(spiffe("td-1", "am.local"));
        stubEvent(TrustDomainEvent.UNDEPLOY, "td-1", ReferenceType.DOMAIN, "another-domain");

        manager.onEvent(event);

        assertThat(manager.findSpiffeByName("am.local")).isPresent();
        verifyNoInteractions(trustDomainKeyService);
    }

    @Test
    void shouldIgnoreEventForAnotherReferenceType() {
        preload(spiffe("td-1", "am.local"));
        when(event.content()).thenReturn(payload);
        when(event.type()).thenReturn(TrustDomainEvent.UNDEPLOY);
        when(payload.getId()).thenReturn("td-1");
        when(payload.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);

        manager.onEvent(event);

        assertThat(manager.findSpiffeByName("am.local")).isPresent();
        verifyNoInteractions(trustDomainKeyService);
    }

    @Test
    void shouldDropTrustedDomainWhenEventReferencesRemovedEntity() {
        preload(spiffe("td-1", "am.local"));
        stubEvent(TrustDomainEvent.UPDATE, "td-1");
        when(trustDomainRepository.findById("td-1")).thenReturn(Maybe.empty());

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.findSpiffeByName("am.local")).isEmpty());
        verify(domainReadinessService).pluginRemoved(DOMAIN_ID, "td-1");
    }

    @Test
    void shouldReportFailureWhenEventLoadFails() {
        preload(spiffe("td-1", "am.local"));
        stubEvent(TrustDomainEvent.UPDATE, "td-1");
        when(trustDomainRepository.findById("td-1")).thenReturn(Maybe.error(new IllegalStateException("boom")));

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(domainReadinessService).pluginFailed(DOMAIN_ID, "td-1", "boom"));
    }

    @Test
    void shouldReindexAndEvictKeyMaterialWhenTrustedDomainIsRenamed() {
        preload(spiffe("td-1", "am.local"));
        stubEvent(TrustDomainEvent.UPDATE, "td-1");
        when(trustDomainRepository.findById("td-1")).thenReturn(Maybe.just(spiffe("td-1", "am.renamed")));

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.findSpiffeByName("am.renamed")).isPresent());
        assertThat(manager.findSpiffeByName("am.local")).isEmpty();
        verify(trustDomainKeyService).evict("td-1");
    }

    @Test
    void shouldRemoveTrustedDomainAndEvictKeyMaterialOnUndeploy() {
        preload(spiffe("td-1", "am.local"), tokenExchange("td-2", "issuer-name", "https://issuer.example.com"));
        stubEvent(TrustDomainEvent.UNDEPLOY, "td-2");

        manager.onEvent(event);

        assertThat(manager.findByIssuer("https://issuer.example.com")).isEmpty();
        assertThat(manager.findSpiffeByName("am.local")).isPresent();
        verify(trustDomainKeyService).evict("td-2");
        verify(domainReadinessService).pluginRemoved(DOMAIN_ID, "td-2");
    }

    @Test
    void shouldTolerateUndeployOfUnknownTrustedDomain() {
        preload(spiffe("td-1", "am.local"));
        stubEvent(TrustDomainEvent.UNDEPLOY, "unknown");

        manager.onEvent(event);

        assertThat(manager.findSpiffeByName("am.local")).isPresent();
        verify(domainReadinessService).pluginRemoved(DOMAIN_ID, "unknown");
    }
}
