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
package io.gravitee.am.resource.infobip;

import io.gravitee.am.resource.api.ResourceConfiguration;
import io.gravitee.secrets.api.annotation.Secret;

/**
 * @author Ruan Ferreira (ruan@incentive.me)
 * @author Incentive.me
 */
public class InfobipResourceConfiguration implements ResourceConfiguration  {
    private String applicationId;
    private String messageId;
    private String baseUrl;
    @Secret
    private String apiKey;
    private String apiKeyPrefix;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String authentication) {
        this.apiKey = authentication;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyPrefix() { return apiKeyPrefix; }

    public void setApiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; }
}
