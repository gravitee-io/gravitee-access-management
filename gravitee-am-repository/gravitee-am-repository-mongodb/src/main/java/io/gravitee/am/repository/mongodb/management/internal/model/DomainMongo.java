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

import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.OIDCSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.UMASettingsMongo;
import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class DomainMongo extends Auditable {

    @BsonId
    private String id;

    private String hrid;

    private String name;

    private DomainVersion version;

    private String description;

    private boolean enabled;

    private boolean alertEnabled;

    private String path;

    private boolean vhostMode;

    private List<VirtualHost> vhosts;

    private OIDCSettingsMongo oidc;

    private UMASettingsMongo uma;

    private SCIMSettingsMongo scim;

    private LoginSettingsMongo loginSettings;

    private WebAuthnSettingsMongo webAuthnSettings;

    private AccountSettingsMongo accountSettings;

    private PasswordSettingsMongo passwordSettings;

    private SelfServiceAccountManagementSettingsMongo selfServiceAccountManagementSettings;

    private SAMLSettingsMongo saml;

    private CorsSettings corsSettings;

    private PostLoginActionMongo postLoginAction;

    private Set<String> tags;

    private ReferenceType referenceType;

    private String referenceId;

    private Set<String> identities;

    private boolean master;

    private String dataPlaneId;

    private SecretSettingsMongo secretSettings;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DomainMongo that = (DomainMongo) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
