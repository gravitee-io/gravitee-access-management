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
package io.gravitee.am.common.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author GraviteeSource Team
 */
class DataPlaneEventTest {

    @Test
    void actionOf_mapsTheLifecycleActions() {
        assertEquals(DataPlaneEvent.DEPLOY, DataPlaneEvent.actionOf(Action.CREATE));
        assertEquals(DataPlaneEvent.UPDATE, DataPlaneEvent.actionOf(Action.UPDATE));
        assertEquals(DataPlaneEvent.UNDEPLOY, DataPlaneEvent.actionOf(Action.DELETE));
    }

    @ParameterizedTest
    @EnumSource(value = Action.class, names = {"BULK_CREATE", "BULK_UPDATE", "BULK_DELETE"})
    void actionOf_ignoresBulkActions(Action action) {
        assertNull(DataPlaneEvent.actionOf(action));
    }

    @Test
    void valueOf_resolvesDataPlaneEvents() {
        assertEquals(DataPlaneEvent.DEPLOY, Event.valueOf(Type.DATA_PLANE, Action.CREATE));
        assertEquals(DataPlaneEvent.UPDATE, Event.valueOf(Type.DATA_PLANE, Action.UPDATE));
        assertEquals(DataPlaneEvent.UNDEPLOY, Event.valueOf(Type.DATA_PLANE, Action.DELETE));
    }
}
