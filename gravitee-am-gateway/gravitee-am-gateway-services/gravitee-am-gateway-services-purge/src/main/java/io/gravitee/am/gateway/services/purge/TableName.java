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

import java.util.Arrays;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum TableName {
    access_tokens,
    authorization_codes,
    refresh_tokens,
    scope_approvals,
    request_objects,
    login_attempts,
    uma_permission_ticket,
    auth_flow_ctx,
    pushed_authorization_requests,
    ciba_auth_requests,
    user_activities,
    devices,
    events;

    public static Optional<TableName> getValueOf(String value) {
        return Arrays.stream(TableName.values()).filter(tableName -> tableName.name().equals(value)).findFirst();
    }
}
