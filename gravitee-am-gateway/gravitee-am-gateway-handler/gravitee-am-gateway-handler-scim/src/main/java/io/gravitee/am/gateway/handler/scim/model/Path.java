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

import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.scim.parser.SCIMFilterParser;

/**
 * The "path" attribute value for the PATCH operation.
 *
 * The "path" is a String containing an attribute path
 *    describing the target of the operation.
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.2">3.5.2.  Modifying with PATCH</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Path {

    private String attributePath;
    private String subAttribute;
    private Filter valuePath;

    public Path() {
    }

    public Path(String attributePath, String subAttribute, String valuePath) {
        this.attributePath = attributePath;
        this.subAttribute = subAttribute;
        this.valuePath = valuePath != null ? SCIMFilterParser.parse(valuePath) : null;
    }

    public String getAttributePath() {
        return attributePath;
    }

    public void setAttributePath(String attributePath) {
        this.attributePath = attributePath;
    }

    public String getSubAttribute() {
        return subAttribute;
    }

    public void setSubAttribute(String subAttribute) {
        this.subAttribute = subAttribute;
    }

    public Filter getValuePath() {
        return valuePath;
    }

    public void setValuePath(Filter valuePath) {
        this.valuePath = valuePath;
    }
}
