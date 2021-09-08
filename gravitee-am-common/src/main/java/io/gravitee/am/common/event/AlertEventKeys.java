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
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AlertEventKeys {

    String PROCESSOR_GEOIP = "geoip";
    String PROCESSOR_USERAGENT = "useragent";
    String CONTEXT_NODE_ID = "node.id";
    String CONTEXT_NODE_HOSTNAME = "node.hostname";
    String CONTEXT_NODE_APPLICATION = "node.application";
    String CONTEXT_GATEWAY_PORT = "gateway.port";
    String PROPERTY_DOMAIN = "domain";
    String PROPERTY_APPLICATION = "application";
    String PROPERTY_USER = "user";
    String PROPERTY_IP = "ip";
    String PROPERTY_USER_AGENT = "user_agent";
    String PROPERTY_TRANSACTION_ID = "transaction_id";
    String PROPERTY_AUTHENTICATION_STATUS = "authentication.status";
    String PROPERTY_ENVIRONMENT = "environment";
    String PROPERTY_ORGANIZATION = "organization";
    String TYPE_AUTHENTICATION = "AUTHENTICATION";
}
