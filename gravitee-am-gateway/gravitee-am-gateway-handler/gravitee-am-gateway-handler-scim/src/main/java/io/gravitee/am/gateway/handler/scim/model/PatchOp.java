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
package io.gravitee.am.gateway.handler.scim.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.2">3.5.2. Modifying with PATCH</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchOp extends Resource {

    public static final List<String> SCHEMAS = Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:PatchOp");

    /**
     * A multi-valued list of complex objects containing the
     *       requested resources.  This MAY be a subset of the full set of
     *       resources if pagination (Section 3.4.2.4) is requested.  REQUIRED
     *       if "totalResults" is non-zero.
     */
    @JsonProperty("Operations")
    private List<Operation> operations;

    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }
}
