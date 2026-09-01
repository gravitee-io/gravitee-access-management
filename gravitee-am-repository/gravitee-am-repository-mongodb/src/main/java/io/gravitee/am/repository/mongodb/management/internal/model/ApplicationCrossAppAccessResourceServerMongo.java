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

import io.gravitee.am.model.application.ApplicationCrossAppAccessResourceServer;

/**
 * MongoDB representation of {@link ApplicationCrossAppAccessResourceServer}.
 */
public class ApplicationCrossAppAccessResourceServerMongo {

    private String trustDomainId;
    private String resourceServerId;
    private String clientId;

    public String getTrustDomainId() {
        return trustDomainId;
    }

    public void setTrustDomainId(String trustDomainId) {
        this.trustDomainId = trustDomainId;
    }

    public String getResourceServerId() {
        return resourceServerId;
    }

    public void setResourceServerId(String resourceServerId) {
        this.resourceServerId = resourceServerId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public ApplicationCrossAppAccessResourceServer convert() {
        ApplicationCrossAppAccessResourceServer resourceServer = new ApplicationCrossAppAccessResourceServer();
        resourceServer.setTrustDomainId(getTrustDomainId());
        resourceServer.setResourceServerId(getResourceServerId());
        resourceServer.setClientId(getClientId());
        return resourceServer;
    }

    public static ApplicationCrossAppAccessResourceServerMongo convert(ApplicationCrossAppAccessResourceServer resourceServer) {
        if (resourceServer == null) {
            return null;
        }
        ApplicationCrossAppAccessResourceServerMongo mongo = new ApplicationCrossAppAccessResourceServerMongo();
        mongo.setTrustDomainId(resourceServer.getTrustDomainId());
        mongo.setResourceServerId(resourceServer.getResourceServerId());
        mongo.setClientId(resourceServer.getClientId());
        return mongo;
    }
}
