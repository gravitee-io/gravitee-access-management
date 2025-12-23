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
package io.gravitee.am.identityprovider.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.provider.impl.MongoConnectionProvider;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

@Slf4j
public abstract class MongoAbstractProvider implements InitializingBean {

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private ConnectionProvider commonConnectionProvider;

    @Autowired
    private IdentityProvider identityProviderEntity;

    @Autowired
    protected MongoIdentityProviderConfiguration configuration;

    @Autowired
    protected Environment environment;

    @Autowired
    private DataSourcesConfiguration dataSourcesConfiguration;

    protected ClientWrapper<MongoClient> clientWrapper;

    protected MongoClient mongoClient;

    protected static final String JSON_SPECIAL_CHARS = "\\{\\}\\[\\],:";
    protected static final String QUOTE = "\"";
    protected static final String SAFE_QUOTE_REPLACEMENT = "\\\\\\\\\\\\" + QUOTE;
    protected static final int MAX_RECURSION_DEPTH = 100;


    /**
     * This provider is used to create MongoClient when the main backend is JDBC/R2DBC because in that case the commonConnectionProvider will provide R2DBC ConnectionPool.
     * This is useful if the user want to create a Mongo IDP when the main backend if a RDBMS.
     */
    private final MongoConnectionProvider mongoProvider = new MongoConnectionProvider();

    @Override
    public void afterPropertiesSet() {
        final var systemScope = Scope.fromName(environment.getProperty("repositories.system-cluster", String.class, Scope.MANAGEMENT.getName()));
        if (!(systemScope == Scope.MANAGEMENT || systemScope == Scope.GATEWAY)) {
            throw new IllegalStateException("Unable to initialize Mongo Identity Provider, repositories.system-cluster only accept 'management' or 'gateway'");
        }

        this.clientWrapper = this.buildClientWrapper(systemScope);
        this.mongoClient = this.clientWrapper.getClient();
    }

    private ClientWrapper<MongoClient> buildClientWrapper(Scope scope) {
        if (shouldUseDatasource()) {
            return configureDatasourceClient();
        }

        if (!this.commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)) {
            return mongoProvider.getClientFromConfiguration(this.configuration);
        }

        return this.identityProviderEntity != null && this.identityProviderEntity.isSystem()
                ? this.commonConnectionProvider.getClientWrapper()
                : getClientWrapperBasedOnConfig(scope);
    }

    private boolean shouldUseDatasource() {
        return StringUtils.hasLength(this.configuration.getDatasourceId()) &&
                this.commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO);
    }

    private ClientWrapper<MongoClient> configureDatasourceClient() {
        final String datasourceId = this.configuration.getDatasourceId();
        final String propertyPrefix = determinePrefixFromDataSourceId(datasourceId);

        // Override the database with the value provided by the datasource
        final String databaseName = environment.getProperty(propertyPrefix + "dbname");
        if (databaseName == null || databaseName.isEmpty()) {
            throw new IllegalStateException("No `dbname` property found for datasource: " + datasourceId);
        }

        log.debug("Configuring Mongo Provider with datasource ID={}, prefix={}, database={}",datasourceId, propertyPrefix, databaseName);
        this.configuration.setDatabase(databaseName);
        return this.commonConnectionProvider.getClientWrapperFromDatasource(datasourceId, propertyPrefix);
    }

    private ClientWrapper<MongoClient> getClientWrapperBasedOnConfig(Scope scope) {
        return this.configuration.isUseSystemCluster()
                ? this.commonConnectionProvider.getClientWrapper(scope.getName())
                : this.commonConnectionProvider.getClientFromConfiguration(this.configuration);
    }

    protected String getSafeUsername(String username) {
        return username.replace(QUOTE, SAFE_QUOTE_REPLACEMENT);
    }
    
    protected String convertToJsonString(String rawString) {
        return rawString
                .replaceAll("[^" + JSON_SPECIAL_CHARS + "\\s]+", "\"$0\"")
                .replaceAll("\\s+", "");
    }

    protected Bson buildUsernameQuery(String findQuery, String username, boolean isCaseSensitive) {
        final String safeUsername = getSafeUsername(username);
        final String jsonQuery = convertToJsonString(findQuery).replace("?", safeUsername);
        final BsonDocument bsonDocument = BsonDocument.parse(jsonQuery);

        if (isCaseSensitive) {
            return bsonDocument;
        }

        final Document document = new Document(bsonDocument);
        final String usernameField = this.configuration.getUsernameField();
        applyCaseInsensitiveUsernameMatching(document, usernameField, username);

        return document;
    }

    protected void applyCaseInsensitiveUsernameMatching(Document document, String usernameField, String username) {
        applyCaseInsensitiveUsernameMatchingToDocument(document, usernameField, username, 0);
    }

    private void applyCaseInsensitiveUsernameMatchingToDocument(Document document, String usernameField, String username, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            log.warn("Maximum recursion depth reached while applying case-insensitive username matching.");
            return;
        }

        // Apply case-insensitive matching to the username field
        document.computeIfPresent(usernameField, (key, value) -> {
            if (value instanceof String || value instanceof BsonString) {
                String escapedValue = Pattern.quote(username);
                return Pattern.compile("^" + escapedValue + "$", Pattern.CASE_INSENSITIVE);
            }
            return value;
        });

        // Recursively check nested documents and lists for username field
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            applyCaseInsensitiveUsernameMatchingToValue(entry.getValue(), usernameField, username, depth + 1);
        }
    }

    private void applyCaseInsensitiveUsernameMatchingToValue(Object value, String usernameField, String username, int depth) {
        if (value instanceof Document nestedDocument) {
            applyCaseInsensitiveUsernameMatchingToDocument(nestedDocument, usernameField, username, depth);
        } else if (value instanceof List<?> nestedList) {
            applyCaseInsensitiveUsernameMatchingToList(nestedList, usernameField, username, depth);
        }
    }

    private void applyCaseInsensitiveUsernameMatchingToList(List<?> list, String usernameField, String username, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            log.warn("Maximum recursion depth reached while applying case-insensitive username matching.");
            return;
        }

        for (Object item : list) {
            applyCaseInsensitiveUsernameMatchingToValue(item, usernameField, username, depth + 1);
        }
    }

    private String determinePrefixFromDataSourceId(String datasourceId) {
        var prefix = dataSourcesConfiguration.getDataSourceKeyById(datasourceId);
        if (!isNull(prefix)) {
            return prefix + ".settings.";
        }

        throw new IllegalArgumentException("No datasource found for id: " + datasourceId);
    }
}
