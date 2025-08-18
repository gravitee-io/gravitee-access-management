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
package io.gravitee.am.gateway.services.purge;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class TableNameTest {

    @Test
    public void shouldIncludeAllTableNames() {
        TableName[] actual = TableName.values();

        TableName[] expected = new TableName[] {
            TableName.access_tokens,
            TableName.authorization_codes,
            TableName.refresh_tokens,
            TableName.scope_approvals,
            TableName.request_objects,
            TableName.login_attempts,
            TableName.uma_permission_ticket,
            TableName.auth_flow_ctx,
            TableName.pushed_authorization_requests,
            TableName.ciba_auth_requests,
            TableName.user_activities,
            TableName.devices,
            TableName.events
        };
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }

    @Test
    public void getValueOf_shouldResolveEveryDeclaredEnum() {
        for (TableName t : TableName.values()) {
            assertThat(TableName.getValueOf(t.name()))
                .as("getValueOf should resolve %s", t.name())
                .hasValue(t);
        }
    }

    @Test
    public void getValueOf_shouldReturnEmptyForUnknown() {
        assertThat(TableName.getValueOf("non_existent_table")).isEmpty();
    }
}