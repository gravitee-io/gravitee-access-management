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

import java.io.Serializable;
import java.util.Objects;

public record Reference(ReferenceType type, String id) implements Serializable {

    public Reference {
        Objects.requireNonNull(type, "reference type cannot be null");
        Objects.requireNonNull(id, "reference id cannot be null");
    }

    @Override
    public String toString() {
        return type + "_" + id;
    }

    public boolean matches(ReferenceType referenceType, String id) {
        return type == referenceType && this.id.equals(id);
    }

    public static Reference organization(String id) {
        return new Reference(ReferenceType.ORGANIZATION, id);
    }

    public static Reference domain(String id) {
        return new Reference(ReferenceType.DOMAIN, id);
    }

    public static Reference environment(String id) {
        return new Reference(ReferenceType.ENVIRONMENT, id);
    }

    public static Reference application(String id) {
        return new Reference(ReferenceType.APPLICATION, id);
    }

    public static Reference protectedResource(String id) {
        return new Reference(ReferenceType.PROTECTED_RESOURCE, id);
    }

}
