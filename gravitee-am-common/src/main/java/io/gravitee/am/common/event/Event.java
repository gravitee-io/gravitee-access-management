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
public abstract class Event {

    public static Enum valueOf(Type type, Action action) {
        switch (type) {
            case DOMAIN:
                return DomainEvent.actionOf(action);
            case APPLICATION:
                return ApplicationEvent.actionOf(action);
            case CERTIFICATE:
                return CertificateEvent.actionOf(action);
            case EXTENSION_GRANT:
                return ExtensionGrantEvent.actionOf(action);
            case IDENTITY_PROVIDER:
                return IdentityProviderEvent.actionOf(action);
            case ROLE:
                return RoleEvent.actionOf(action);
            case SCOPE:
                return ScopeEvent.actionOf(action);
            case FORM:
                return FormEvent.actionOf(action);
            case EMAIL:
                return EmailEvent.actionOf(action);
            case REPORTER:
                return ReporterEvent.actionOf(action);
            case POLICY:
                return PolicyEvent.actionOf(action);
            case USER:
                return UserEvent.actionOf(action);
            case GROUP:
                return GroupEvent.actionOf(action);
            case MEMBERSHIP:
                return MembershipEvent.actionOf(action);
            case FACTOR:
                return FactorEvent.actionOf(action);
            case FLOW:
                return FlowEvent.actionOf(action);
            case ALERT_TRIGGER:
                return AlertTriggerEvent.actionOf(action);
            case ALERT_NOTIFIER:
                return AlertNotifierEvent.actionOf(action);
            case RESOURCE:
                return ResourceEvent.actionOf(action);
            case BOT_DETECTION:
                return BotDetectionEvent.actionOf(action);
            case DEVICE_IDENTIFIER:
                return DeviceIdentifierEvent.actionOf(action);
            default:
                return null;
        }
    }
}
