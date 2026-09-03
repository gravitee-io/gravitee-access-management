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
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pins the storage location of a Mongo identity provider that reuses the system cluster: the
 * database is the one the serving node reads and the collection is derived from the identity
 * provider id. Only providers created under this regime are affected, so an existing provider keeps
 * working whatever the setting becomes later.
 *
 * <p>Under that regime a provider's use of the system cluster is settled at creation, whether or not
 * the platform pinned it.
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

    static final String COLLECTION_PREFIX = "idp_";

    static final String SYSTEM_CLUSTER_CANNOT_BE_CHANGED =
            "useSystemCluster cannot be changed after the identity provider has been created.";

    private static final String USE_SYSTEM_CLUSTER = "useSystemCluster";
    private static final String DATASOURCE_ID = "datasourceId";
    private static final String DATABASE = "database";
    private static final String USERS_COLLECTION = "usersCollection";

    private final SystemClusterDatabaseResolver databaseResolver;

    private final SystemClusterIdpSettings settings;

    public SystemClusterIdpPolicy(SystemClusterDatabaseResolver databaseResolver, SystemClusterIdpSettings settings) {
        this.databaseResolver = databaseResolver;
        this.settings = settings;
    }

    public void applyOnUpdate(IdentityProvider stored, IdentityProvider identityToUpdate) {
        if (stored.isSystemClusterRestricted()) {
            checkStorageUnchanged(stored, identityToUpdate.getConfiguration());
            return;
        }
        if (ownsStorageLocation() && isEligible(identityToUpdate)) {
            checkSystemClusterUnchanged(stored, identityToUpdate);
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

        pinDatabase(identityProvider, configuration);
        configuration.put(USERS_COLLECTION, COLLECTION_PREFIX + identityProvider.getId());

        identityProvider.setConfiguration(JSONObjectUtils.toJSONString(configuration));
        identityProvider.setSystemClusterRestricted(true);
    }

    /** The configuration to store and the database it now names, for the caller to report. */
    public record RepinnedDatabase(String configuration, String database) {
    }

    /**
     * The database of a pinned provider is written from the platform settings, so it goes stale when
     * those settings change - a data plane added or moved, a connection uri rewritten. Re-reads it,
     * and returns empty when the stored configuration already names the right database.
     */
    public Optional<RepinnedDatabase> refreshPinnedDatabase(IdentityProvider identityProvider) {
        if (!identityProvider.isSystemClusterRestricted() || !isEligible(identityProvider)) {
            return Optional.empty();
        }
        final Map<String, Object> configuration = parse(identityProvider.getConfiguration());
        if (configuration == null || !isUseSystemCluster(configuration) || hasDatasourceId(configuration)) {
            return Optional.empty();
        }
        final String database = databaseResolver.resolve(identityProvider);
        if (database == null || database.isEmpty() || database.equals(configuration.get(DATABASE))) {
            return Optional.empty();
        }
        configuration.put(DATABASE, database);
        return Optional.of(new RepinnedDatabase(JSONObjectUtils.toJSONString(configuration), database));
    }

    private void pinDatabase(IdentityProvider identityProvider, Map<String, Object> configuration) {
        // The key stays in the configuration: the plugin schema requires it unless a datasource
        // is named, so removing it would leave the stored configuration invalid and block every
        // later update. Each node still overrides it from its own client wrapper at runtime.
        final String database = databaseResolver.resolve(identityProvider);
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

    /**
     * True when the platform owns where a provider created now stores its users: it names both the
     * database and the collection, so nothing outside the platform may choose either, nor the id
     * the collection is named after.
     */
    public boolean ownsStorageLocation() {
        return settings.isRestricted();
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

    private void checkSystemClusterUnchanged(IdentityProvider stored, IdentityProvider identityToUpdate) {
        final Map<String, Object> current = parse(stored.getConfiguration());
        final Map<String, Object> updated = parse(identityToUpdate.getConfiguration());
        if (current == null || updated == null) {
            return;
        }
        if (isUseSystemCluster(current) != isUseSystemCluster(updated)) {
            throw new InvalidParameterException(SYSTEM_CLUSTER_CANNOT_BE_CHANGED);
        }
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
