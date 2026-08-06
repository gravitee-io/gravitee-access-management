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
package io.gravitee.am.common.oidc.command;

/**
 * Commands defined by OpenID Provider Commands 1.0 (draft).
 * The pipeline is command-agnostic: a command is only a value carried in the
 * event payload and in the command token's <code>command</code> claim.
 * Only account lifecycle commands are listed; tenant commands and the
 * metadata command are out of scope.
 *
 * See https://openid.net/specs/openid-provider-commands-1_0.html
 *
 * @author GraviteeSource Team
 */
public enum Command {

    INVALIDATE("invalidate"),
    SUSPEND("suspend"),
    REACTIVATE("reactivate"),
    DELETE("delete"),
    ARCHIVE("archive"),
    RESTORE("restore");

    private final String value;

    Command(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Command fromValue(String value) {
        for (Command command : values()) {
            if (command.value.equals(value)) {
                return command;
            }
        }
        throw new IllegalArgumentException("Unknown command: " + value);
    }
}
