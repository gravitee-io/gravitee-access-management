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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ComplexType {

    /**
     * A Boolean value specifying whether or not the operation is supported. REQUIRED.
     */
    private boolean supported;

    /**
     * An integer value specifying the maximum number of operations. REQUIRED.
     */
    private Integer maxOperations;

    /**
     * An integer value specifying the maximum payload size in bytes. REQUIRED.
     */
    private Integer maxPayloadSize;

    /**
     * An integer value specifying the maximum number of resources returned in a response. REQUIRED.
     */
    private Integer maxResults;

    public ComplexType(boolean supported) {
        this.supported = supported;
    }

    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public Integer getMaxOperations() {
        return maxOperations;
    }

    public void setMaxOperations(Integer maxOperations) {
        this.maxOperations = maxOperations;
    }

    public Integer getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public void setMaxPayloadSize(Integer maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
