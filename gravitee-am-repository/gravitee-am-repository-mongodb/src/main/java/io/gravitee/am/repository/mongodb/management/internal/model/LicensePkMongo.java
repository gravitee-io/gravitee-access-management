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
package io.gravitee.am.repository.mongodb.management.internal.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite {@code _id} for the {@code licenses} collection: the (referenceType, referenceId) pair.
 *
 * @author GraviteeSource Team
 */
public class LicensePkMongo implements Serializable {

    private String referenceType;

    private String referenceId;

    public LicensePkMongo() {
    }

    public LicensePkMongo(String referenceType, String referenceId) {
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LicensePkMongo)) return false;
        LicensePkMongo that = (LicensePkMongo) o;
        return Objects.equals(referenceType, that.referenceType) && Objects.equals(referenceId, that.referenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceType, referenceId);
    }
}
