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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class NoOpDataPlaneVerifierTest {

    private static final String DATA_PLANE_ID = "dp-1";

    @Mock
    private DataPlaneProvider provider;

    private final NoOpDataPlaneVerifier verifier = new NoOpDataPlaneVerifier();

    @Test
    void should_serve_a_data_plane_that_was_never_required() {
        verifier.verified(DATA_PLANE_ID).test().assertComplete();
    }

    @Test
    void should_serve_a_required_data_plane_without_asking_its_store() {
        verifier.require(DATA_PLANE_ID, provider);

        verifier.verified(DATA_PLANE_ID).test().assertComplete();
        verifyNoInteractions(provider);
    }

    @Test
    void should_serve_a_reserved_data_plane_rather_than_refuse_it() {
        // a claim only means "not checked yet", which is never a reason to refuse here
        verifier.reserve(DATA_PLANE_ID);

        verifier.verified(DATA_PLANE_ID).test().assertComplete();
    }

    @Test
    void should_serve_a_forgotten_data_plane() {
        verifier.require(DATA_PLANE_ID, provider);

        verifier.forget(DATA_PLANE_ID);

        verifier.verified(DATA_PLANE_ID).test().assertComplete();
    }
}
