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

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Tripwire: every ApplicationType must be classified as either an agent type or a non-agent type.
 * <p>
 * The Applications UI lists non-agent applications by sending the full non-agent type set as the
 * {@code type} query parameter on {@code GET /applications}. The Agents UI does the same with the
 * agent set. If a new value is added to {@link ApplicationType} without updating
 * {@code AGENT_TYPES}/{@code NON_AGENT_TYPES} here AND in {@code application.service.ts} on the
 * UI side, the new type will silently disappear from one or both screens.
 * <p>
 * When this test fails: decide which screen the new type belongs to, add it to the right set
 * here, then mirror the change in {@code gravitee-am-ui/src/app/services/application.service.ts}
 * ({@code ALL_APPLICATION_TYPES} / {@code AGENT_APPLICATION_TYPES}).
 */
public class ApplicationTypeClassificationTest {

    private static final Set<ApplicationType> AGENT_TYPES = EnumSet.of(ApplicationType.AGENT);
    private static final Set<ApplicationType> NON_AGENT_TYPES = EnumSet.of(
            ApplicationType.WEB,
            ApplicationType.NATIVE,
            ApplicationType.BROWSER,
            ApplicationType.SERVICE,
            ApplicationType.RESOURCE_SERVER
    );

    @Test
    public void everyApplicationTypeIsClassified() {
        Set<ApplicationType> classified = EnumSet.noneOf(ApplicationType.class);
        classified.addAll(AGENT_TYPES);
        classified.addAll(NON_AGENT_TYPES);

        assertEquals("Every ApplicationType must be classified as agent or non-agent — see class javadoc.",
                EnumSet.allOf(ApplicationType.class), classified);
    }

    @Test
    public void agentAndNonAgentSetsAreDisjoint() {
        Set<ApplicationType> intersection = EnumSet.copyOf(AGENT_TYPES);
        intersection.retainAll(NON_AGENT_TYPES);
        assertEquals(EnumSet.noneOf(ApplicationType.class), intersection);
    }
}
