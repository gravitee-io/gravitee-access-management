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
class AuthorizationDetailTypeEventTest {

    @Test
    void shouldMapLifecycleActions() {
        assertEquals(AuthorizationDetailTypeEvent.DEPLOY, AuthorizationDetailTypeEvent.actionOf(Action.CREATE));
        assertEquals(AuthorizationDetailTypeEvent.UPDATE, AuthorizationDetailTypeEvent.actionOf(Action.UPDATE));
        assertEquals(AuthorizationDetailTypeEvent.UNDEPLOY, AuthorizationDetailTypeEvent.actionOf(Action.DELETE));
    }

    @ParameterizedTest
    @EnumSource(value = Action.class, names = {"BULK_CREATE", "BULK_UPDATE", "BULK_DELETE"})
    void shouldIgnoreBulkActions(Action action) {
        assertNull(AuthorizationDetailTypeEvent.actionOf(action));
    }

    @Test
    void shouldResolveAuthorizationDetailTypeEvents() {
        assertEquals(AuthorizationDetailTypeEvent.DEPLOY, Event.valueOf(Type.AUTHORIZATION_DETAIL_TYPE, Action.CREATE));
        assertEquals(AuthorizationDetailTypeEvent.UPDATE, Event.valueOf(Type.AUTHORIZATION_DETAIL_TYPE, Action.UPDATE));
        assertEquals(AuthorizationDetailTypeEvent.UNDEPLOY, Event.valueOf(Type.AUTHORIZATION_DETAIL_TYPE, Action.DELETE));
    }
}
