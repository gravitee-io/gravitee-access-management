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

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Installation {

    public static final String COCKPIT_INSTALLATION_ID = "COCKPIT_INSTALLATION_ID";
    public static final String COCKPIT_URL = "COCKPIT_URL";
    public static final String COCKPIT_INSTALLATION_STATUS = "COCKPIT_INSTALLATION_STATUS";

    /**
     * Auto generated id.
     * This id is generated at the first startup time.
     */
    private String id;

    /**
     * Additional information about this installation.
     */
    private Map<String, String> additionalInformation = new HashMap<>();

    /**
     * Creation date.
     */
    @Schema(type = "java.lang.Long")
    private Date createdAt;

    /**
     * Last update date.
     */
    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public Installation() {
    }

    public Installation(Installation other) {

        this.id = other.id;
        this.additionalInformation = other.additionalInformation;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, String> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, String> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
