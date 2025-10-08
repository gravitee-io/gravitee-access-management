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
public final class UpgraderOrder {
    public static final int INSTALLATION_UPGRADER = 0;
    public static final int DEFAULT_ROLE_UPGRADER = 1;
    public static final int DEFAULT_ORG_UPGRADER = 2;
    public static final int DEFAULT_ENV_UPGRADER = 3;
    public static final int SCOPE_UPGRADER = 4;
    public static final int OPENID_SCOPE_UPGRADER = 5;
    public static final int DOMAIN_UPGRADER = 6;
    public static final int DOMAIN_IDP_UPGRADER = 7;
    public static final int DOMAIN_REPORTER_UPGRADER = 8;
    public static final int POLICY_FLOW_UPGRADER = 9;
    public static final int APPLICATION_SCOPE_SETTINGS_UPGRADER = 10;
    public static final int APPLICATION_IDENTITY_PROVIDER_UPGRADER = 13;
    public static final int SYSTEM_CERTIFICATE_UPGRADER = 14;
    public static final int APPLICATION_FACTOR_UPGRADER = 15;
    public static final int DOMAIN_PASSWORD_POLICIES_UPGRADER = 16;
    public static final int ORG_DEFAULT_REPORTER_UPGRADER = 17;
    public static final int DOMAIN_DATA_PLANE_UPGRADER = 18;
    public static final int IDP_DATA_PLANE_UPGRADER = 19;
    public static final int APPLICATION_CLIENT_SECRETS_UPGRADER = 20;

    private UpgraderOrder() {
        throw new UnsupportedOperationException("utility class, don't instantiate");
    }
}
