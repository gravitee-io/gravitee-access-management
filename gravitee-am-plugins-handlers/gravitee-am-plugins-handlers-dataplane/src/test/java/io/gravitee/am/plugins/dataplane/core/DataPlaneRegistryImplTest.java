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
package io.gravitee.am.plugins.dataplane.core;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.dataplane.exceptions.IllegalDataPlaneIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class DataPlaneRegistryImplTest {

    private static final DataPlaneDescription DESCRIPTION =
            new DataPlaneDescription("dp-1", "name", "jdbc", "dataPlanes.provisioned.dp-1", "https://gw");

    @Mock
    private DataPlanePluginManager dataPlanePluginManager;

    @Mock
    private DataPlaneProvider provider;

    @Mock
    private DataPlaneVerifier verifier;

    private DataPlaneRegistryImpl registry() {
        return new DataPlaneRegistryImpl(storage -> {}, dataPlanePluginManager, verifier);
    }

    @Test
    void should_register_a_description_whose_provider_was_built() {
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.of(provider));
        DataPlaneRegistryImpl registry = registry();

        registry.register(DESCRIPTION);

        assertThat(registry.getDataPlanes()).containsExactly(DESCRIPTION);
        assertThat(registry.getProviderById("dp-1")).isSameAs(provider);
    }

    @Test
    void should_not_register_a_description_whose_provider_could_not_be_built() {
        // otherwise a domain can bind to a data plane nothing can serve, and cannot then be deleted
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.empty());
        DataPlaneRegistryImpl registry = registry();

        assertThatThrownBy(() -> registry.register(DESCRIPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dp-1");

        assertThat(registry.getDataPlanes()).isEmpty();
        assertThatThrownBy(() -> registry.getProviderById("dp-1"))
                .isInstanceOf(IllegalDataPlaneIdException.class);
    }

    @Test
    void should_reject_a_second_definition_claiming_a_registered_id() {
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.of(provider));
        DataPlaneRegistryImpl registry = registry();
        registry.register(DESCRIPTION);

        assertThatThrownBy(() -> registry.register(DESCRIPTION))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_reject_a_definition_with_no_id() {
        DataPlaneRegistryImpl registry = registry();

        assertThatThrownBy(() -> registry.register(new DataPlaneDescription("", "name", "jdbc", "base", "gw")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_leave_a_failed_id_free_for_a_later_definition() {
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.empty(), Optional.of(provider));
        DataPlaneRegistryImpl registry = registry();
        assertThatThrownBy(() -> registry.register(DESCRIPTION)).isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> registry.register(DESCRIPTION)).doesNotThrowAnyException();
        assertThat(registry.getProviderById("dp-1")).isSameAs(provider);
    }

    @Test
    void should_stop_and_drop_an_unregistered_provider() {
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.of(provider));
        DataPlaneRegistryImpl registry = registry();
        registry.register(DESCRIPTION);

        registry.unregister("dp-1");

        verify(provider).stop();
        // a re-provisioned id must be checked again, not inherit the outgoing definition's result
        verify(verifier).forget("dp-1");
        assertThat(registry.getDataPlanes()).isEmpty();
        assertThatThrownBy(() -> registry.getProviderById("dp-1")).isInstanceOf(IllegalDataPlaneIdException.class);
    }

    @Test
    void should_let_an_unregistered_id_be_claimed_again() {
        // a deleted definition and a new one under the same id must not resolve to the old provider
        DataPlaneProvider replacement = mock(DataPlaneProvider.class);
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.of(provider), Optional.of(replacement));
        DataPlaneRegistryImpl registry = registry();
        registry.register(DESCRIPTION);

        registry.unregister("dp-1");
        registry.register(DESCRIPTION);

        assertThat(registry.getProviderById("dp-1")).isSameAs(replacement);
    }

    @Test
    void should_claim_a_provisioned_plane_before_registering_it() {
        // a domain creation landing between the two must not read the gap as "nothing to check"
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.of(provider));
        DataPlaneRegistryImpl registry = registry();

        registry.registerProvisioned(DESCRIPTION);

        InOrder inOrder = inOrder(verifier, dataPlanePluginManager);
        inOrder.verify(verifier).reserve("dp-1");
        inOrder.verify(dataPlanePluginManager).create(DESCRIPTION);
        inOrder.verify(verifier).require("dp-1", provider);
    }

    @Test
    void should_release_the_claim_when_a_provisioned_plane_cannot_be_built() {
        // the claim must not outlive the failed registration, or the id stays refused for good
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.empty());
        DataPlaneRegistryImpl registry = registry();

        assertThatThrownBy(() -> registry.registerProvisioned(DESCRIPTION))
                .isInstanceOf(IllegalStateException.class);

        verify(verifier).forget("dp-1");
        verify(verifier, never()).require(any(), any());
    }

    @Test
    void should_not_claim_a_plane_registered_without_verification() {
        // the gravitee.yml planes are exempt, and go through register rather than registerProvisioned
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.of(provider));

        registry().register(DESCRIPTION);

        verify(verifier, never()).reserve(any());
        verify(verifier, never()).require(any(), any());
    }

    @Test
    void should_ignore_unregistering_an_unknown_id() {
        assertThatCode(() -> registry().unregister("never-registered")).doesNotThrowAnyException();
    }

    @Test
    void should_free_the_id_even_when_the_provider_will_not_stop() {
        when(dataPlanePluginManager.create(DESCRIPTION)).thenReturn(Optional.of(provider), Optional.of(provider));
        doThrow(new IllegalStateException("connection pool is wedged")).when(provider).stop();
        DataPlaneRegistryImpl registry = registry();
        registry.register(DESCRIPTION);

        assertThatCode(() -> registry.unregister("dp-1")).doesNotThrowAnyException();
        assertThatCode(() -> registry.register(DESCRIPTION)).doesNotThrowAnyException();
    }
}
