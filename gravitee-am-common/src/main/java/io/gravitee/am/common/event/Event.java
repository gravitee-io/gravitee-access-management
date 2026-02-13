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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class Event {

    public static Enum<?> valueOf(Type type, Action action) {
        return switch (type) {
            case DOMAIN -> DomainEvent.actionOf(action);
            case APPLICATION -> ApplicationEvent.actionOf(action);
            case APPLICATION_SECRET -> ApplicationSecretEvent.actionOf(action);
            case CERTIFICATE -> CertificateEvent.actionOf(action);
            case EXTENSION_GRANT -> ExtensionGrantEvent.actionOf(action);
            case IDENTITY_PROVIDER -> IdentityProviderEvent.actionOf(action);
            case ROLE -> RoleEvent.actionOf(action);
            case SCOPE -> ScopeEvent.actionOf(action);
            case FORM -> FormEvent.actionOf(action);
            case EMAIL -> EmailEvent.actionOf(action);
            case REPORTER -> ReporterEvent.actionOf(action);
            case POLICY -> PolicyEvent.actionOf(action);
            case GROUP -> GroupEvent.actionOf(action);
            case MEMBERSHIP -> MembershipEvent.actionOf(action);
            case FACTOR -> FactorEvent.actionOf(action);
            case FLOW -> FlowEvent.actionOf(action);
            case ALERT_TRIGGER -> AlertTriggerEvent.actionOf(action);
            case ALERT_NOTIFIER -> AlertNotifierEvent.actionOf(action);
            case RESOURCE -> ResourceEvent.actionOf(action);
            case BOT_DETECTION -> BotDetectionEvent.actionOf(action);
            case DEVICE_IDENTIFIER -> DeviceIdentifierEvent.actionOf(action);
            case AUTH_DEVICE_NOTIFIER -> AuthenticationDeviceNotifierEvent.actionOf(action);
            case I18N_DICTIONARY -> I18nDictionaryEvent.actionOf(action);
            case THEME -> ThemeEvent.actionOf(action);
            case PASSWORD_POLICY -> PasswordPolicyEvent.actionOf(action);
            case REVOKE_TOKEN -> RevokeTokenEvent.actionOf(action);
            case USER -> UserEvent.actionOf(action);
            case AUTHORIZATION_ENGINE ->  AuthorizationEngineEvent.actionOf(action);
            case PROTECTED_RESOURCE -> ProtectedResourceEvent.actionOf(action);
            case PROTECTED_RESOURCE_SECRET -> ProtectedResourceSecretEvent.actionOf(action);
            case DOMAIN_CERTIFICATE_SETTINGS -> DomainCertificateSettingsEvent.actionOf(action);
            default -> null;
        };
    }
}
