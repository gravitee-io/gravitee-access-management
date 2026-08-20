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
package io.gravitee.am.service.idp;

import com.nimbusds.jose.util.JSONObjectUtils;
import io.gravitee.am.common.env.CloudProperties;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.exception.InvalidParameterException;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Pins the storage location of a Mongo identity provider that reuses the system cluster: the
 * database comes from the repository layer settings and the collection is derived from the
 * identity provider id. Only providers created under this regime are affected, so an existing
 * provider keeps working whatever the settings become later.
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class SystemClusterIdpPolicy {

    /**
     * Held as a string so that gravitee-am-service keeps no dependency on the Mongo identity
     * provider plugin.
     */
    public static final String MONGO_IDP_TYPE = "mongo-am-idp";

    static final String SYSTEM_CLUSTER = "repositories.system-cluster";
    static final String DEFAULT_SYSTEM_CLUSTER = "management";
    static final String COLLECTION_PREFIX = "idp_";

    private static final String USE_SYSTEM_CLUSTER = "useSystemCluster";
    private static final String DATASOURCE_ID = "datasourceId";
    private static final String DATABASE = "database";
    private static final String USERS_COLLECTION = "usersCollection";

    @Autowired
    private Environment environment;

    /**
     * Rewrites the configuration of a newly created identity provider and flags it, when the
     * platform owns its storage location.
     */
    public void applyOnCreate(IdentityProvider identityProvider) {
        if (!CloudProperties.isManagedCloudEnabled(environment)) {
            return;
        }
        if (identityProvider.isSystem() || !MONGO_IDP_TYPE.equals(identityProvider.getType())) {
            return;
        }
        final Map<String, Object> configuration = parse(identityProvider.getConfiguration());
        if (configuration == null || !isUseSystemCluster(configuration) || hasDatasourceId(configuration)) {
            return;
        }

        // The key stays in the configuration: the plugin schema requires it unless a datasource is
        // named, so removing it would leave the stored configuration invalid and block every later
        // update. Each node still overrides it from its own client wrapper at runtime.
        final String database = resolvePlatformDatabase();
        if (database != null && !database.isEmpty()) {
            configuration.put(DATABASE, database);
        } else {
            log.warn("Cannot pin the database of identity provider {}: no dbname configured for the system cluster",
                    identityProvider.getId());
        }
        configuration.put(USERS_COLLECTION, COLLECTION_PREFIX + identityProvider.getId());

        identityProvider.setConfiguration(JSONObjectUtils.toJSONString(configuration));
        identityProvider.setSystemClusterRestricted(true);
    }

    /**
     * Rejects an update that moves a pinned identity provider to another storage location.
     */
    public void checkOnUpdate(IdentityProvider stored, String newConfiguration) {
        if (!stored.isSystemClusterRestricted()) {
            return;
        }
        final Map<String, Object> current = parse(stored.getConfiguration());
        final Map<String, Object> updated = parse(newConfiguration);
        if (current == null || updated == null) {
            return;
        }
        if (!Objects.equals(current.get(USE_SYSTEM_CLUSTER), updated.get(USE_SYSTEM_CLUSTER))
                || !Objects.equals(current.get(USERS_COLLECTION), updated.get(USERS_COLLECTION))
                || !Objects.equals(current.get(DATABASE), updated.get(DATABASE))) {
            throw new InvalidParameterException("Identity provider storage settings cannot be changed");
        }
    }

    private String resolvePlatformDatabase() {
        final String scope = environment.getProperty(SYSTEM_CLUSTER, String.class, DEFAULT_SYSTEM_CLUSTER);
        return environment.getProperty("repositories." + scope + ".mongodb.dbname");
    }

    private boolean isUseSystemCluster(Map<String, Object> configuration) {
        return Boolean.TRUE.equals(configuration.get(USE_SYSTEM_CLUSTER));
    }

    private boolean hasDatasourceId(Map<String, Object> configuration) {
        final Object datasourceId = configuration.get(DATASOURCE_ID);
        return datasourceId instanceof String value && !value.isEmpty();
    }

    private Map<String, Object> parse(String configuration) {
        if (configuration == null) {
            return null;
        }
        try {
            return JSONObjectUtils.parse(configuration);
        } catch (ParseException e) {
            log.warn("Unable to parse configuration for identity provider", e);
            return null;
        }
    }
}
