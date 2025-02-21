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

/**
 * This event is used to ask for a token to be revoked by the Gateway.
 * This is typically used by the Management API when a user is deleted
 * to notify the Gateway than the delete need to be done no the AccessTokenRepository
 * which is linked to the OAuth2 scope. This event has been introduced with the DataPlane
 * split as due to this split Management API doesn't have direct access to the OAuth2 & Gateway
 * repository scopes.
 *
 * @author GraviteeSource Team
 */
public enum RevokeTokenEvent {

    REVOKE;

    public static RevokeTokenEvent actionOf(Action action) {
        return switch (action) {
            case DELETE -> RevokeTokenEvent.REVOKE;
            default -> null;
        };
    }
}
