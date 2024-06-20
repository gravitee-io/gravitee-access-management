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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class ReporterMongo extends Auditable {

    @BsonId
    private String id;

    /**
     * @deprecated use referenceType & referenceId instead
     */

    // Lombok copies @deprecated to the generated accessors; mongo codec loses its mind when it sees @Deprecated both
    // on a field and an accessor. So we define them manually.
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String domain;

    @Deprecated(since="4.5.0", forRemoval = true)
    public String getDomain() {
        return domain;
    }

    @Deprecated(since="4.5.0", forRemoval = true)
    public void setDomain(String domain) {
        this.domain = domain;
    }

    private ReferenceType referenceType;
    private String referenceId;

    private boolean enabled;

    private String name;

    private boolean system;

    private String type;

    private String dataType;

    private String configuration;



}
