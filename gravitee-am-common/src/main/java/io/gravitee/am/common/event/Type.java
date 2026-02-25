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
package io.gravitee.am.common.event;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Type {

    DOMAIN,
    APPLICATION,
    APPLICATION_SECRET,
    IDENTITY_PROVIDER,
    CERTIFICATE,
    EXTENSION_GRANT,
    SCOPE,
    ROLE,
    FORM,
    EMAIL,
    REPORTER,
    POLICY,
    USER,
    MEMBERSHIP,
    GROUP,
    FACTOR,
    RESOURCE,
    FLOW,
    ALERT_TRIGGER,
    ALERT_NOTIFIER,
    BOT_DETECTION,
    AUTH_DEVICE_NOTIFIER,
    DEVICE_IDENTIFIER,
    I18N_DICTIONARY,
    THEME,
    PASSWORD_POLICY,
    AUTHORIZATION_ENGINE,
    AUTHORIZATION_BUNDLE,
    UNKNOWN, // used during unmarshalling to avoid Exception which will block the sync process
    REVOKE_TOKEN,
    PROTECTED_RESOURCE,
    PROTECTED_RESOURCE_SECRET,
    DOMAIN_CERTIFICATE_SETTINGS
}
