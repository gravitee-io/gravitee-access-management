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

import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers {@link ConsentRelayStrategy} implementations on the plugin classpath (via
 * {@code META-INF/spring.factories}) and indexes them by id. The core plugin ships none — a blank
 * notifier config therefore relays authorization_details unchanged.
 */
public final class ConsentRelayStrategyRegistry {

    private final Map<String, ConsentRelayStrategy> byId;

    public ConsentRelayStrategyRegistry(List<ConsentRelayStrategy> strategies) {
        Map<String, ConsentRelayStrategy> m = new LinkedHashMap<>();
        for (ConsentRelayStrategy s : strategies) {
            if (m.putIfAbsent(s.id(), s) != null) {
                throw new IllegalStateException("Duplicate consentRelayStrategy id: " + s.id());
            }
        }
        this.byId = Map.copyOf(m);
    }

    /** Discover strategies from {@code cl} (the plugin classloader at runtime — spans the plugin lib/). */
    public static ConsentRelayStrategyRegistry discover(ClassLoader cl) {
        List<ConsentRelayStrategy> found = SpringFactoriesLoader.loadFactories(ConsentRelayStrategy.class, cl);
        return new ConsentRelayStrategyRegistry(found);
    }

    /** Resolve a configured id to a strategy, or throw listing the installed ids. */
    public ConsentRelayStrategy resolve(String id) {
        ConsentRelayStrategy s = byId.get(id);
        if (s == null) {
            throw new IllegalArgumentException("unknown consentRelayStrategy '" + id
                    + "' (installed: " + String.join(", ", byId.keySet()) + ")");
        }
        return s;
    }
}
