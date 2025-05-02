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
package io.gravitee.am.management.service.impl.notifications;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementUINotifierConfiguration {

    public static final String CERTIFICATE_EXPIRY_TPL = "certificate_expiration";
    public static final String CLIENT_SECRET_EXPIRY_TPL = "client_secret_expiration";

    private String template;

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public static ManagementUINotifierConfiguration certificateExpiration() {
        ManagementUINotifierConfiguration cfg = new ManagementUINotifierConfiguration();
        cfg.setTemplate(CERTIFICATE_EXPIRY_TPL);
        return cfg;
    }

    public static ManagementUINotifierConfiguration clientSecretExpiration() {
        ManagementUINotifierConfiguration cfg = new ManagementUINotifierConfiguration();
        cfg.setTemplate(CLIENT_SECRET_EXPIRY_TPL);
        return cfg;
    }
}
