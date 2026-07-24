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
 * This event is used to ask the Gateway to dispatch an OpenID Provider Command
 * (https://openid.net/specs/openid-provider-commands-1_0.html) to the applications
 * of a security domain that registered a command_endpoint. It is emitted by the
 * Management API (which has no access to the data-plane repository scopes since
 * the DataPlane split) and consumed by the gateway nodes which stage the command
 * for single-node dispatch.
 *
 * @author GraviteeSource Team
 */
public enum CommandEvent {

    EXECUTE;

    public static CommandEvent actionOf(Action action) {
        return switch (action) {
            case CREATE -> CommandEvent.EXECUTE;
            default -> null;
        };
    }
}
