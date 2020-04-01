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
package io.gravitee.am.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Acl {
    CREATE,
    READ,
    LIST,
    UPDATE,
    DELETE;

    private static final Set<Acl> ALL = Arrays.stream(values()).collect(Collectors.toSet());

    public static Set<Acl> all() {
        return ALL;
    }

    public static Set<Acl> of(Acl... acls) {

        return Arrays.stream(acls).collect(Collectors.toSet());
    }
}
