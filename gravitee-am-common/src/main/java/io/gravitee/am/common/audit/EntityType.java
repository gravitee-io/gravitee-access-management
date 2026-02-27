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
package io.gravitee.am.common.audit;

/**
 * Audit entity types
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EntityType {
    String APPLICATION = "APPLICATION";
    String DOMAIN = "DOMAIN";
    String CERTIFICATE = "CERTIFICATE";
    String EXTENSION_GRANT = "EXTENSION_GRANT";
    String USER = "USER";
    String GROUP = "GROUP";
    String ROLE = "ROLE";
    String SCOPE = "SCOPE";
    String IDENTITY_PROVIDER = "IDENTITY_PROVIDER";
    String AUTHORIZATION_ENGINE = "AUTHORIZATION_ENGINE";
    String EMAIL = "EMAIL";
    String FORM = "FORM";
    String REPORTER = "REPORTER";
    String TAG = "TAG";
    String MEMBERSHIP = "MEMBERSHIP";
    String FACTOR = "FACTOR";
    String ORGANIZATION = "ORGANIZATION";
    String ENVIRONMENT = "ENVIRONMENT";
    String ENTRYPOINT = "ENTRYPOINT";
    String FLOW = "FLOW";
    String ALERT_TRIGGER = "ALERT_TRIGGER";
    String ALERT_NOTIFIER = "ALERT_NOTIFIER";
    String RESOURCE = "RESOURCE";
    String BOT_DETECTION = "BOT_DETECTION";
    String DEVICE_IDENTIFIER = "DEVICE_IDENTIFIER";
    String AUTH_DEVICE_NOTIFIER = "AUTHENTICATION_DEVICE_NOTIFIER";
    String CREDENTIAL = "CREDENTIAL";
    String I18N_DICTIONARY = "I18N_DICTIONARY";
    String THEME = "THEME";
    String MFA_FACTOR= "MFA_FACTOR";
    String PASSWORD_POLICY= "PASSWORD_POLICY";
    String PROTECTED_RESOURCE = "PROTECTED_RESOURCE";
    String POLICY_SET = "POLICY_SET";
    String AUTHORIZATION_SCHEMA = "AUTHORIZATION_SCHEMA";
    String ENTITY_STORE = "ENTITY_STORE";
    String AUTHORIZATION_BUNDLE = "AUTHORIZATION_BUNDLE";

}
