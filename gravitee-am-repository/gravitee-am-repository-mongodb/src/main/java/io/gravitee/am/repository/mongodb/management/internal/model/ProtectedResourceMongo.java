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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.repository.mongodb.common.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProtectedResourceMongo extends Auditable {
    public final static String CLIENT_ID_FIELD = "clientId";
    public final static String DOMAIN_ID_FIELD = "domainId";
    public final static String TYPE_FIELD = "type";
    public final static String UPDATED_AT_FIELD = "updatedAt";


    private String id;

    private String name;

    private String clientId;

    private String domainId;

    private String description;

    private String type;

    private List<String> resourceIdentifiers;

    private List<ClientSecretMongo> clientSecrets;

    private List<ApplicationSecretSettingsMongo> secretSettings;

}
