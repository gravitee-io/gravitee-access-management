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
package io.gravitee.am.gateway.core.event;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class Event {

    public static Enum valueOf(io.gravitee.am.model.common.event.Event event) {
        Enum event1 = null;
        switch (event.getType()) {
            case DOMAIN:
                event1 =  DomainEvent.actionOf(event.getPayload().getAction());
                break;
            case CLIENT:
                event1 =  ClientEvent.actionOf(event.getPayload().getAction());
                break;
            case CERTIFICATE:
                event1 =  CertificateEvent.actionOf(event.getPayload().getAction());
                break;
            case EXTENSION_GRANT:
                event1 =  ExtensionGrantEvent.actionOf(event.getPayload().getAction());
                break;
            case IDENTITY_PROVIDER:
                event1 =  IdentityProviderEvent.actionOf(event.getPayload().getAction());
                break;
            case ROLE:
                event1 =  RoleEvent.actionOf(event.getPayload().getAction());
                break;
            case SCOPE:
                event1 =  ScopeEvent.actionOf(event.getPayload().getAction());
                break;
            case FORM:
                event1 =  FormEvent.actionOf(event.getPayload().getAction());
                break;
            case EMAIL:
                event1 =  EmailEvent.actionOf(event.getPayload().getAction());
                break;
            case REPORTER:
                event1 =  ReporterEvent.actionOf(event.getPayload().getAction());
                break;
            case POLICY:
                event1 =  PolicyEvent.actionOf(event.getPayload().getAction());
                break;
        }

        return event1;
    }
}
