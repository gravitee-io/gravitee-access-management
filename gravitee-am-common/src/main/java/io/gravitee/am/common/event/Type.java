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

    ALERT_NOTIFIER,
    ALERT_TRIGGER,
    APPLICATION,
    APPLICATION_SECRET,
    AUTH_DEVICE_NOTIFIER,
    AUTHORIZATION_ENGINE,
    BOT_DETECTION,
    CERTIFICATE,
    DEVICE_IDENTIFIER,
    DOMAIN,
    EMAIL,
    EXTENSION_GRANT,
    FACTOR,
    FLOW,
    FORM,
    GROUP,
    I18N_DICTIONARY,
    IDENTITY_PROVIDER,
    MEMBERSHIP,
    PASSWORD_POLICY,
    POLICY,
    PROTECTED_RESOURCE,
    REPORTER,
    RESOURCE,
    REVOKE_TOKEN,
    ROLE,
    SCOPE,
    THEME,
    USER,
    UNKNOWN // used during unmarshalling to avoid Exception which will block the sync process
}
