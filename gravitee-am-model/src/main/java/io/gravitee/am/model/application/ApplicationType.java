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
package io.gravitee.am.model.application;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum ApplicationType {
    WEB, NATIVE, BROWSER, SERVICE, RESOURCE_SERVER, AGENT;

    public static ApplicationType orNull(String type) {
        if (type == null) {
            return null;
        }

        try {
            return ApplicationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Resolves the effective set of application types to filter on for the generic
     * applications listing: when no type is explicitly requested, every type except
     * {@link #AGENT} is returned so agents are excluded from the standard listing.
     * Agents remain reachable by requesting {@code AGENT} explicitly.
     */
    public static Set<ApplicationType> defaultingToNonAgent(Collection<ApplicationType> requested) {
        return (requested == null || requested.isEmpty())
                ? EnumSet.complementOf(EnumSet.of(AGENT))
                : new HashSet<>(requested);
    }
}
