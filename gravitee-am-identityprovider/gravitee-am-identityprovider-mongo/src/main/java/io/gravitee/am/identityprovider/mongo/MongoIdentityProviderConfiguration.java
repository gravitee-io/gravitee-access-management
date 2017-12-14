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

import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoIdentityProviderConfiguration implements IdentityProviderConfiguration {
    private String uri;
    private String host;
    private int port;
    private boolean enableCredentials;
    private String databaseCredentials;
    private String usernameCredentials;
    private String passwordCredentials;
    private String database;
    private String usersCollection;
    private String findUserByUsernameQuery;
    private String passwordField;

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isEnableCredentials() {
        return this.enableCredentials;
    }

    public void setEnableCredentials(boolean enableCredentials) {
        this.enableCredentials = enableCredentials;
    }

    public String getDatabaseCredentials() {
        return this.databaseCredentials;
    }

    public void setDatabaseCredentials(String databaseCredentials) {
        this.databaseCredentials = databaseCredentials;
    }

    public String getUsernameCredentials() {
        return this.usernameCredentials;
    }

    public void setUsernameCredentials(String usernameCredentials) {
        this.usernameCredentials = usernameCredentials;
    }

    public String getPasswordCredentials() {
        return this.passwordCredentials;
    }

    public void setPasswordCredentials(String passwordCredentials) {
        this.passwordCredentials = passwordCredentials;
    }

    public String getDatabase() {
        return this.database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsersCollection() {
        return this.usersCollection;
    }

    public void setUsersCollection(String usersCollection) {
        this.usersCollection = usersCollection;
    }

    public String getFindUserByUsernameQuery() {
        return this.findUserByUsernameQuery;
    }

    public void setFindUserByUsernameQuery(String findUserByUsernameQuery) {
        this.findUserByUsernameQuery = findUserByUsernameQuery;
    }

    public String getPasswordField() {
        return this.passwordField;
    }

    public void setPasswordField(String passwordField) {
        this.passwordField = passwordField;
    }
}
