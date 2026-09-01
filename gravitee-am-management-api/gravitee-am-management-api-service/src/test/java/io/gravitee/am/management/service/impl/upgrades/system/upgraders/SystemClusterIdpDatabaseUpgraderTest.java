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
package io.gravitee.am.management.service.impl.upgrades.system.upgraders;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.idp.SystemClusterIdpPolicy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemClusterIdpDatabaseUpgraderTest {

    private static final SystemClusterIdpPolicy.RepinnedDatabase REPINNED =
            new SystemClusterIdpPolicy.RepinnedDatabase("{\"database\":\"dp1-db\"}", "dp1-db");

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private SystemClusterIdpPolicy systemClusterIdpPolicy;

    @InjectMocks
    private SystemClusterIdpDatabaseUpgrader upgrader;

    private final IdentityProvider pinnedIdp = identityProvider(true);
    private final IdentityProvider ownStorageIdp = identityProvider(false);

    @Test
    void should_realign_a_pinned_identity_provider_whose_database_went_stale() throws Exception {
        when(identityProviderService.findAll()).thenReturn(Flowable.just(pinnedIdp));
        when(systemClusterIdpPolicy.refreshPinnedDatabase(pinnedIdp)).thenReturn(Optional.of(REPINNED));
        when(identityProviderService.updatePinnedStorage(pinnedIdp, REPINNED.configuration())).thenReturn(Single.just(pinnedIdp));

        awaitUpgrade();

        verify(identityProviderService, times(1)).updatePinnedStorage(pinnedIdp, REPINNED.configuration());
    }

    @Test
    void should_leave_a_pinned_identity_provider_alone_when_its_database_is_the_right_one() throws Exception {
        // No event is emitted on a start that has nothing to repair, so the gateways are left alone.
        when(identityProviderService.findAll()).thenReturn(Flowable.just(pinnedIdp));
        when(systemClusterIdpPolicy.refreshPinnedDatabase(pinnedIdp)).thenReturn(Optional.empty());

        awaitUpgrade();

        verify(identityProviderService, never()).updatePinnedStorage(any(), anyString());
    }

    @Test
    void should_skip_an_identity_provider_that_owns_its_storage() throws Exception {
        when(identityProviderService.findAll()).thenReturn(Flowable.just(ownStorageIdp));

        awaitUpgrade();

        verify(systemClusterIdpPolicy, never()).refreshPinnedDatabase(any());
        verify(identityProviderService, never()).updatePinnedStorage(any(), anyString());
    }

    @Test
    void should_report_a_failed_update_so_the_node_stops() {
        when(identityProviderService.findAll()).thenReturn(Flowable.just(pinnedIdp));
        when(systemClusterIdpPolicy.refreshPinnedDatabase(pinnedIdp)).thenReturn(Optional.of(REPINNED));
        when(identityProviderService.updatePinnedStorage(eq(pinnedIdp), anyString()))
                .thenReturn(Single.error(new IllegalStateException("boom")));

        upgrader.upgrade().test().assertError(IllegalStateException.class);
    }

    private void awaitUpgrade() throws InterruptedException {
        TestObserver<Void> observer = upgrader.upgrade().test();
        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
    }

    private static IdentityProvider identityProvider(boolean systemClusterRestricted) {
        var identityProvider = new IdentityProvider();
        identityProvider.setId("idp-1");
        identityProvider.setType(SystemClusterIdpPolicy.MONGO_IDP_TYPE);
        identityProvider.setSystemClusterRestricted(systemClusterRestricted);
        identityProvider.setConfiguration("{\"database\":\"management-db\"}");
        return identityProvider;
    }
}
