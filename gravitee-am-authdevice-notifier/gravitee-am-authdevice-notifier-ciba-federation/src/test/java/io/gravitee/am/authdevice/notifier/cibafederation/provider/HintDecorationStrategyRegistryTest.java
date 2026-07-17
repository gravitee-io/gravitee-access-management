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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HintDecorationStrategyRegistryTest {

    static final class NoopHint implements HintDecorationStrategy {
        public String id() { return "noop"; }
        public CibaHints decorate(CibaHints in, HintDecorationContext ctx) { return in; }
    }

    @Test void resolves_registered_id() {
        var reg = new HintDecorationStrategyRegistry(List.of(new NoopHint()));
        assertEquals("noop", reg.resolve("noop").id());
    }

    @Test void unknown_id_throws_listing_installed() {
        var reg = new HintDecorationStrategyRegistry(List.of(new NoopHint()));
        var ex = assertThrows(IllegalArgumentException.class, () -> reg.resolve("missing"));
        assertTrue(ex.getMessage().contains("noop"));
    }

    @Test void duplicate_id_rejected() {
        var ex = assertThrows(IllegalStateException.class,
                () -> new HintDecorationStrategyRegistry(List.of(new NoopHint(), new NoopHint())));
        assertTrue(ex.getMessage().contains("noop"));
    }
}
