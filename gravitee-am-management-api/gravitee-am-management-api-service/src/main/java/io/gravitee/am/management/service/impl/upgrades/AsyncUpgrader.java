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

import io.gravitee.node.api.upgrader.Upgrader;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapts async upgraders to the sync {@link Upgrader} interface in a consistent way.
 */
@Slf4j
public abstract class AsyncUpgrader implements Upgrader {
    @Override
    public final boolean upgrade() {
        return doUpgrade()
                .toSingleDefault(true)
                .onErrorReturn(ex -> {
                    log.error("Unable to apply {}: ", this.getClass().getSimpleName(), ex);
                    return false;
                })
                .blockingGet();
    }

    abstract Completable doUpgrade();
}
