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
package io.gravitee.am.management.service.impl.upgrades;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UpgraderOrder {
    int INSTALLATION_UPGRADER = 0;
    int DEFAULT_ROLE_UPGRADER = 1;
    int DEFAULT_ORG_UPGRADER = 2;
    int DEFAULT_ENV_UPGRADER = 3;
    int SCOPE_UPGRADER = 4;
    int OPENID_SCOPE_UPGRADER = 5;
    int DOMAIN_UPGRADER = 6;
    int DOMAIN_IDP_UPGRADER = 7;
    int DOMAIN_REPORTER_UPGRADER = 8;
    int POLICY_FLOW_UPGRADER = 9;
    int APPLICATION_SCOPE_SETTINGS_UPGRADER = 10;
    int DEFAULT_IDP_UPGRADER = 11;
    int DEFAULT_REPORTER_UPGRADER = 12;
    int APPLICATION_IDENTITY_PROVIDER_UPGRADER = 13;
}
