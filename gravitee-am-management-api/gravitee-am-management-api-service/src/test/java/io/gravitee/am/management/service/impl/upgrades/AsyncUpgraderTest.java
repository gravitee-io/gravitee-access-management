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
package io.gravitee.am.management.service.impl.upgrades;

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncUpgraderTest {

    @Test
    void shouldHandleErrors() {
        var upgrader = new TestUpgrader(0, () -> Completable.error(new RuntimeException("test exception")));
        assertThatCode(upgrader::upgrade).doesNotThrowAnyException();

    }


    private static class TestUpgrader extends AsyncUpgrader {
        private final Supplier<Completable> upgrade;
        private final int order;

        private TestUpgrader(int order, Supplier<Completable> doUpgrade) {
            this.upgrade = doUpgrade;
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        Completable doUpgrade() {
            return upgrade.get();
        }
    }
}
