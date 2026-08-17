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

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class DataPlaneVerifierImplTest {

    private static final String DATA_PLANE_ID = "dp-1";

    @Mock
    private DataPlaneProvider provider;

    private final AtomicInteger checks = new AtomicInteger();

    /** Counts how often the store was actually asked, which is what the cache and the cooldown govern. */
    private Completable answering(Completable outcome) {
        return Completable.defer(() -> {
            checks.incrementAndGet();
            return outcome;
        });
    }

    @Test
    void should_serve_a_data_plane_that_was_never_required() {
        var verifier = new DataPlaneVerifierImpl(5000, 10000);

        verifier.verified(DATA_PLANE_ID).test().assertComplete();
    }

    @Test
    void should_ask_the_store_once_and_reuse_the_answer() {
        when(provider.healthCheck()).thenReturn(answering(Completable.complete()));
        var verifier = new DataPlaneVerifierImpl(5000, 10000);

        verifier.require(DATA_PLANE_ID, provider);
        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        verifier.verified(DATA_PLANE_ID).test().assertComplete();

        assertThat(checks).hasValue(1);
    }

    @Test
    void should_refuse_a_data_plane_whose_store_does_not_answer() {
        when(provider.healthCheck()).thenReturn(answering(Completable.error(new IOException("refused"))));
        var verifier = new DataPlaneVerifierImpl(5000, 10000);

        verifier.require(DATA_PLANE_ID, provider);

        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertError(IOException.class);
    }

    @Test
    void should_answer_from_the_refusal_rather_than_ask_again_within_the_cooldown() {
        when(provider.healthCheck()).thenReturn(answering(Completable.error(new IOException("refused"))));
        var verifier = new DataPlaneVerifierImpl(5000, 60000);

        verifier.require(DATA_PLANE_ID, provider);
        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertError(IOException.class);
        verifier.verified(DATA_PLANE_ID).test().assertError(IOException.class);
        verifier.verified(DATA_PLANE_ID).test().assertError(IOException.class);

        // a store that is down must not take one connection attempt per caller
        assertThat(checks).hasValue(1);
    }

    @Test
    void should_ask_the_store_again_once_the_cooldown_has_passed() {
        when(provider.healthCheck()).thenReturn(answering(Completable.error(new IOException("refused"))));
        var verifier = new DataPlaneVerifierImpl(5000, 0);

        verifier.require(DATA_PLANE_ID, provider);
        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertError(IOException.class);
        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertError(IOException.class);

        // a refusal that outlived its cooldown must not keep the id unusable
        assertThat(checks).hasValue(2);
    }

    @Test
    void should_forget_a_refusal_along_with_the_data_plane() {
        when(provider.healthCheck()).thenReturn(answering(Completable.error(new IOException("refused"))));
        var verifier = new DataPlaneVerifierImpl(5000, 60000);
        verifier.require(DATA_PLANE_ID, provider);
        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertError(IOException.class);

        verifier.forget(DATA_PLANE_ID);

        // the id is free again, so a re-provisioned data plane is not answered from the old refusal
        verifier.verified(DATA_PLANE_ID).test().assertComplete();
    }

    @Test
    void should_refuse_a_reserved_data_plane_until_its_provider_arrives() {
        var verifier = new DataPlaneVerifierImpl(5000, 10000);

        verifier.reserve(DATA_PLANE_ID);

        // the gap between registering a data plane and putting it under verification must not read
        // as "nothing to check"
        verifier.verified(DATA_PLANE_ID).test().assertError(IllegalStateException.class);
        assertThat(checks).hasValue(0);
    }

    @Test
    void should_serve_a_reserved_data_plane_once_its_provider_has_answered() {
        when(provider.healthCheck()).thenReturn(answering(Completable.complete()));
        var verifier = new DataPlaneVerifierImpl(5000, 10000);

        verifier.reserve(DATA_PLANE_ID);
        verifier.require(DATA_PLANE_ID, provider);

        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        assertThat(checks).hasValue(1);
    }

    @Test
    void should_release_a_reservation_that_was_forgotten() {
        var verifier = new DataPlaneVerifierImpl(5000, 10000);
        verifier.reserve(DATA_PLANE_ID);

        verifier.forget(DATA_PLANE_ID);

        // a registration that failed must not leave the id refused for good
        verifier.verified(DATA_PLANE_ID).test().assertComplete();
    }

    @Test
    void should_ignore_a_refusal_belonging_to_a_definition_that_was_already_replaced() {
        var inFlight = CompletableSubject.create();
        when(provider.healthCheck()).thenReturn(answering(inFlight));
        var verifier = new DataPlaneVerifierImpl(5000, 60000);
        verifier.require(DATA_PLANE_ID, provider);
        // subscribed here rather than left to the background check, so the store is asked before the
        // definition is replaced whichever way the scheduler runs
        var waiting = verifier.verified(DATA_PLANE_ID).test();
        assertThat(checks).hasValue(1);

        // the definition is replaced by one that works while its check is still waiting on the store
        verifier.forget(DATA_PLANE_ID);
        when(provider.healthCheck()).thenReturn(answering(Completable.complete()));
        verifier.require(DATA_PLANE_ID, provider);
        inFlight.onError(new IOException("refused"));
        waiting.awaitDone(5, TimeUnit.SECONDS).assertError(IOException.class);

        // the outgoing definition's refusal must not be recorded against the one that replaced it
        verifier.verified(DATA_PLANE_ID).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
    }
}
