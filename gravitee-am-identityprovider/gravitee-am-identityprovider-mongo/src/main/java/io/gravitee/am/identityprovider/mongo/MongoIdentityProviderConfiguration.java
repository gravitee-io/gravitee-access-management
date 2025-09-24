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

import io.gravitee.am.common.password.PasswordSaltFormat;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoderOptions;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoIdentityProviderConfiguration implements IdentityProviderConfiguration, MongoConnectionConfiguration {
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_PASSWORD = "password";
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
    private String findUserByMultipleFieldsQuery;
    private String findUserByEmailQuery = "{email: ?}";
    private String usernameField = FIELD_USERNAME;
    private String passwordField = FIELD_PASSWORD;
    private String passwordEncoder = PasswordEncoder.BCRYPT;
    private String passwordEncoding = "Base64";
    private boolean useDedicatedSalt;
    private String passwordSaltAttribute = "salt";
    private Integer passwordSaltLength = 32;
    private String passwordSaltFormat = PasswordSaltFormat.DIGEST;

    private boolean userProvider = true;

    private boolean usernameCaseSensitive = false;

    private PasswordEncoderOptions passwordEncoderOptions;

    private boolean useSystemCluster;
    private String datasourceId;

    @Override
    public boolean userProvider() {
        return this.userProvider;
    }

    public void setUserProvider(boolean userProvider) {
        this.userProvider = userProvider;
    }

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

    public Integer getPort() {
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

    public String getFindUserByEmailQuery() {
        return findUserByEmailQuery;
    }

    public void setFindUserByEmailQuery(String findUserByEmailQuery) {
        this.findUserByEmailQuery = findUserByEmailQuery;
    }

    public String getFindUserByMultipleFieldsQuery() {
        return findUserByMultipleFieldsQuery;
    }

    public void setFindUserByMultipleFieldsQuery(String findUserByMultipleFieldsQuery) {
        this.findUserByMultipleFieldsQuery = findUserByMultipleFieldsQuery;
    }

    public String getUsernameField() {
        return usernameField;
    }

    public void setUsernameField(String usernameField) {
        this.usernameField = usernameField;
    }

    public String getPasswordField() {
        return this.passwordField;
    }

    public void setPasswordField(String passwordField) {
        this.passwordField = passwordField;
    }

    public String getPasswordEncoder() {
        return passwordEncoder;
    }

    public void setPasswordEncoder(String passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String getPasswordEncoding() {
        return passwordEncoding;
    }

    public void setPasswordEncoding(String passwordEncoding) {
        this.passwordEncoding = passwordEncoding;
    }

    public boolean isUseDedicatedSalt() {
        return useDedicatedSalt;
    }

    public void setUseDedicatedSalt(boolean useDedicatedSalt) {
        this.useDedicatedSalt = useDedicatedSalt;
    }

    public String getPasswordSaltAttribute() {
        return passwordSaltAttribute;
    }

    public void setPasswordSaltAttribute(String passwordSaltAttribute) {
        this.passwordSaltAttribute = passwordSaltAttribute;
    }

    public Integer getPasswordSaltLength() {
        return passwordSaltLength;
    }

    public void setPasswordSaltLength(Integer passwordSaltLength) {
        this.passwordSaltLength = passwordSaltLength;
    }

    public String getPasswordSaltFormat() {
        return passwordSaltFormat;
    }

    public void setPasswordSaltFormat(String passwordSaltFormat) {
        this.passwordSaltFormat = passwordSaltFormat;
    }

    public boolean isUsernameCaseSensitive() {
        return usernameCaseSensitive;
    }

    public void setUsernameCaseSensitive(boolean usernameCaseSensitive) {
        this.usernameCaseSensitive = usernameCaseSensitive;
    }

    public PasswordEncoderOptions getPasswordEncoderOptions() {
        return passwordEncoderOptions;
    }

    public void setPasswordEncoderOptions(PasswordEncoderOptions passwordEncoderOptions) {
        this.passwordEncoderOptions = passwordEncoderOptions;
    }

    public boolean isUseSystemCluster() {
        return useSystemCluster;
    }

    public void setUseSystemCluster(boolean useSystemCluster) {
        this.useSystemCluster = useSystemCluster;
    }

    public String getDatasourceId(){
        return this.datasourceId;
    }

    public void setDatasourceId(String datasourceId){
        this.datasourceId = datasourceId;
    }
}
