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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.exception.InvalidParameterException;
import lombok.CustomLog;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
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
    static final String MONGODB_TYPE = "mongodb";
    static final String DEFAULT_DATABASE = "gravitee-am";

    private static final String USE_SYSTEM_CLUSTER = "useSystemCluster";
    private static final String DATASOURCE_ID = "datasourceId";
    private static final String DATABASE = "database";
    private static final String USERS_COLLECTION = "usersCollection";

    private final Environment environment;

    private final SystemClusterIdpSettings settings;

    public SystemClusterIdpPolicy(Environment environment, SystemClusterIdpSettings settings) {
        this.environment = environment;
        this.settings = settings;
    }

    public void applyOnUpdate(IdentityProvider stored, IdentityProvider identityToUpdate) {
        if (stored.isSystemClusterRestricted()) {
            checkStorageUnchanged(stored, identityToUpdate.getConfiguration());
            return;
        }
        final Map<String, Object> current = parse(stored.getConfiguration());
        // An identity provider that already reuses the system cluster keeps the settings it has
        // today, whatever the mode says now.
        if (current == null || isUseSystemCluster(current)) {
            return;
        }
        rejectSystemClusterSwitch(identityToUpdate);
    }

    // The collection comes from the id, and users already stored elsewhere do not move, so joining
    // the regime later would hide them.
    private void rejectSystemClusterSwitch(IdentityProvider identityToUpdate) {
        if (!ownsStorageLocation() || !isEligible(identityToUpdate)) {
            return;
        }
        final Map<String, Object> updated = parse(identityToUpdate.getConfiguration());
        if (updated != null && isUseSystemCluster(updated) && !hasDatasourceId(updated)) {
            throw new InvalidParameterException("The system cluster can only be selected when the identity provider is created");
        }
    }

    public void applyOnCreate(IdentityProvider identityProvider) {
        if (!ownsStorageLocation() || !isEligible(identityProvider)) {
            return;
        }
        final Map<String, Object> configuration = parse(identityProvider.getConfiguration());
        if (configuration == null || !isUseSystemCluster(configuration) || hasDatasourceId(configuration)) {
            return;
        }

        if (settings.isPinDatabase()) {
            pinDatabase(identityProvider, configuration);
        }
        if (settings.isPrefixUsersCollection()) {
            configuration.put(USERS_COLLECTION, COLLECTION_PREFIX + identityProvider.getId());
        }

        identityProvider.setConfiguration(JSONObjectUtils.toJSONString(configuration));
        identityProvider.setSystemClusterRestricted(true);
    }

    private void pinDatabase(IdentityProvider identityProvider, Map<String, Object> configuration) {
        // The key stays in the configuration: the plugin schema requires it unless a datasource
        // is named, so removing it would leave the stored configuration invalid and block every
        // later update. Each node still overrides it from its own client wrapper at runtime.
        final String database = resolvePlatformDatabase();
        if (database != null && !database.isEmpty()) {
            configuration.put(DATABASE, database);
        } else {
            log.warn("Cannot pin the database of identity provider {}: no uri or dbname configured for the system cluster",
                    identityProvider.getId());
        }
    }

    /**
     * The automation API is declarative: an operator re-applies a manifest that cannot carry a
     * collection named after an id the platform generated, so copy the pinned values onto it.
     */
    public String carryPinnedStorage(IdentityProvider stored, String newConfiguration) {
        if (!stored.isSystemClusterRestricted()) {
            return newConfiguration;
        }
        final Map<String, Object> current = parse(stored.getConfiguration());
        final Map<String, Object> updated = parse(newConfiguration);
        if (current == null || updated == null) {
            return newConfiguration;
        }
        copyStorageField(current, updated, DATABASE);
        copyStorageField(current, updated, USERS_COLLECTION);
        return JSONObjectUtils.toJSONString(updated);
    }

    private void copyStorageField(Map<String, Object> current, Map<String, Object> updated, String field) {
        if (current.containsKey(field)) {
            updated.put(field, current.get(field));
        } else {
            updated.remove(field);
        }
    }

    /** True when either storage rule is on, so a provider created now joins the regime. */
    public boolean ownsStorageLocation() {
        return settings.isPinDatabase() || settings.isPrefixUsersCollection();
    }

    /** The id becomes the collection name, so nothing outside the platform may choose it. */
    public boolean derivesCollectionFromId() {
        return settings.isPrefixUsersCollection();
    }

    private boolean isEligible(IdentityProvider identityProvider) {
        return !identityProvider.isSystem() && MONGO_IDP_TYPE.equals(identityProvider.getType());
    }

    private void checkStorageUnchanged(IdentityProvider stored, String newConfiguration) {
        final Map<String, Object> current = parse(stored.getConfiguration());
        final Map<String, Object> updated = parse(newConfiguration);
        if (current == null || updated == null) {
            throw new InvalidParameterException("Identity provider configuration cannot be read");
        }
        // datasourceId is compared too: naming a datasource moves the users elsewhere at runtime,
        // which is the move this check exists to reject.
        if (!Objects.equals(current.get(USE_SYSTEM_CLUSTER), updated.get(USE_SYSTEM_CLUSTER))
                || !Objects.equals(current.get(USERS_COLLECTION), updated.get(USERS_COLLECTION))
                || !Objects.equals(current.get(DATABASE), updated.get(DATABASE))
                || !Objects.equals(current.get(DATASOURCE_ID), updated.get(DATASOURCE_ID))) {
            throw new InvalidParameterException("Identity provider storage settings cannot be changed");
        }
    }

    /**
     * Mirrors {@code MongoConnectionProvider#getDatabaseName}, which is private to the repository
     * plugin. A node that resolves the database differently from this method pins one value and
     * reads another, so the two must stay in step.
     */
    private String resolvePlatformDatabase() {
        final String scope = resolveDatabaseScope();
        // Only a mongo repository names a database to pin. The packaged gravitee.yml still carries a
        // mongodb block on a jdbc platform, so without this the policy pins a database that has no store.
        if (!MONGODB_TYPE.equalsIgnoreCase(environment.getProperty("repositories." + scope + ".type", MONGODB_TYPE))) {
            return null;
        }
        final String prefix = "repositories." + scope + ".mongodb.";
        final String uri = environment.getProperty(prefix + "uri", "");
        if (!uri.isEmpty()) {
            final String path = URI.create(uri).getPath();
            if (path != null && path.length() > 1) {
                return path.substring(1);
            }
        }
        return environment.getProperty(prefix + "dbname", DEFAULT_DATABASE);
    }

    /**
     * The gateway scope reuses the management settings unless it is told not to, so the database of
     * a provider pinned to the gateway scope usually comes from the management block.
     */
    private String resolveDatabaseScope() {
        final String scope = environment.getProperty(SYSTEM_CLUSTER, String.class, DEFAULT_SYSTEM_CLUSTER);
        if (DEFAULT_SYSTEM_CLUSTER.equals(scope)) {
            return scope;
        }
        final boolean useManagementSettings =
                environment.getProperty("repositories." + scope + ".use-management-settings", Boolean.class, true);
        return useManagementSettings ? DEFAULT_SYSTEM_CLUSTER : scope;
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
