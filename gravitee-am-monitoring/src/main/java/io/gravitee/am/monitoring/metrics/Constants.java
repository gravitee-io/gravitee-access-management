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
package io.gravitee.am.monitoring.metrics;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Constants {
    String METRICS_NAME_PREFIX = "gio_";

    String METRICS_EVENTS_SYNC = METRICS_NAME_PREFIX + "events_sync";

    String METRICS_DOMAINS = METRICS_NAME_PREFIX + "domains";
    String METRICS_DOMAIN_EVENTS = METRICS_NAME_PREFIX + "domain_evt";

    String METRICS_APP = METRICS_NAME_PREFIX + "apps";
    String METRICS_APP_EVENTS = METRICS_NAME_PREFIX + "app_evt";

    String METRICS_IDPS = METRICS_NAME_PREFIX + "idps";
    String METRICS_IDP_EVENTS = METRICS_NAME_PREFIX + "idp_evt";

    String METRICS_AUTH_EVENTS = METRICS_NAME_PREFIX + "auth_evt";

    String METRICS_BUFFERED_EMAILS = METRICS_NAME_PREFIX + "buffered_emails";
    String METRICS_DROPPED_EMAILS = METRICS_NAME_PREFIX + "dropped_emails";

    String TAG_AUTH_STATUS = "auth_status";
    String TAG_AUTH_IDP = "idp";
    String TAG_VALUE_AUTH_IDP_INTERNAL = "internal";
    String TAG_VALUE_AUTH_IDP_EXTERNAL = "external";
}
