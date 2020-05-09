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
package io.gravitee.am.gateway.handler.uma.constants;

public interface UMAConstants {

    String UMA_PATH = "/uma";

    // Provider configuration metadata
    String WELL_KNOWN_PATH="/.well-known/uma2-configuration";

    // UMA Grant claims interaction endpoint.
    String CLAIMS_INTERACTION_PATH = "/claims-gathering";

    // UMA Federated Protection API
    String PERMISSION_PATH = "/protection/permission";
    String RESOURCE_REGISTRATION_PATH = "/protection/resource_set";
    String RESOURCE_ID="resource_id";
}
