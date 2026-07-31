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

import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * A data plane definition persisted in the management repository. It carries the same information
 * as a {@code dataPlanes[i]} entry of the gravitee.yml, plus the environment it is bound to.
 *
 * Not to be confused with {@code io.gravitee.am.dataplane.api.DataPlane}, which is the plugin descriptor.
 *
 * @author GraviteeSource Team
 */
@Data
public class DataPlaneDefinition {

    private String id;

    private String name;

    /**
     * Matches the {@code dataplane-am-<type>} plugin id suffix (mongodb, jdbc, ...).
     */
    private String type;

    private String gatewayUrl;

    private String organizationId;

    private String environmentId;

    /**
     * JSON body of the gravitee.yml {@code dataPlanes[i]} entry, stored verbatim. Can hold
     * connection credentials, so it is kept out of the generated {@code toString()}.
     */
    @ToString.Exclude
    private String configuration;

    private Date createdAt;

    private Date updatedAt;
}
