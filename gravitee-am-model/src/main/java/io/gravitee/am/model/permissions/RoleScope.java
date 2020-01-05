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
package io.gravitee.am.model.permissions;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum RoleScope {
    MANAGEMENT(1),
    DOMAIN(2),
    APPLICATION(3);

    private int id;

    RoleScope(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static RoleScope valueOf(int id) {
        Optional<RoleScope> scope = Stream.of(values()).filter((r) -> r.getId() == id).findFirst();
        if (scope.isPresent()) {
            return scope.get();
        } else {
            throw new IllegalArgumentException(id + " not a RoleScope id");
        }
    }
}
