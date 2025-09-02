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
package io.gravitee.am.identityprovider.jdbc.configuration;

import io.gravitee.am.common.password.PasswordSaltFormat;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder;
import io.gravitee.am.repository.jdbc.provider.R2DBCConnectionConfiguration;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoderOptions;

import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JdbcIdentityProviderConfiguration implements IdentityProviderConfiguration, R2DBCConnectionConfiguration {

    public static final String FIELD_ID = "id";
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_PASSWORD = "password";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_METADATA = "metadata";
    private String host;
    private Integer port;
    private String protocol;
    private String database;
    private String usersTable = "users";
    private String user;
    private String password;
    private String selectUserByUsernameQuery;
    private String selectUserByMultipleFieldsQuery;
    private String selectUserByEmailQuery;
    private String identifierAttribute = FIELD_ID;
    private String emailAttribute = FIELD_EMAIL;
    private String usernameAttribute = FIELD_USERNAME;
    private String passwordAttribute = FIELD_PASSWORD;
    private String passwordEncoder = PasswordEncoder.BCRYPT;
    private String passwordEncoding = "Base64";
    private boolean useDedicatedSalt;
    private String passwordSaltAttribute = "salt";
    private Integer passwordSaltLength = 32;
    private String passwordSaltFormat = PasswordSaltFormat.DIGEST;
    private List<Map<String, String>> options;
    private boolean autoProvisioning = false;

    private boolean userProvider = true;

    private PasswordEncoderOptions passwordEncoderOptions;

    @Override
    public boolean userProvider() {
        return this.userProvider;
    }

    public void setUserProvider(boolean userProvider) {
        this.userProvider = userProvider;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }


    public String getUsersTable() {
        return usersTable;
    }

    public void setUsersTable(String usersTable) {
        this.usersTable = usersTable;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSelectUserByUsernameQuery() {
        return selectUserByUsernameQuery;
    }

    public void setSelectUserByUsernameQuery(String selectUserByUsernameQuery) {
        this.selectUserByUsernameQuery = selectUserByUsernameQuery;
    }

    public String getSelectUserByMultipleFieldsQuery() {
        return selectUserByMultipleFieldsQuery;
    }

    public void setSelectUserByMultipleFieldsQuery(String selectUserByMultipleFieldsQuery) {
        this.selectUserByMultipleFieldsQuery = selectUserByMultipleFieldsQuery;
    }

    public String getSelectUserByEmailQuery() {
        return selectUserByEmailQuery;
    }

    public void setSelectUserByEmailQuery(String selectUserByEmailQuery) {
        this.selectUserByEmailQuery = selectUserByEmailQuery;
    }

    public String getIdentifierAttribute() {
        return identifierAttribute;
    }

    public void setIdentifierAttribute(String identifierAttribute) {
        this.identifierAttribute = identifierAttribute;
    }

    public String getUsernameAttribute() {
        return usernameAttribute;
    }

    public void setUsernameAttribute(String usernameAttribute) {
        this.usernameAttribute = usernameAttribute;
    }

    public String getPasswordAttribute() {
        return passwordAttribute;
    }

    public void setPasswordAttribute(String passwordAttribute) {
        this.passwordAttribute = passwordAttribute;
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

    public List<Map<String, String>> getOptions() {
        return options;
    }

    public void setOptions(List<Map<String, String>> options) {
        this.options = options;
    }

    public String getEmailAttribute() {
        return emailAttribute;
    }

    public void setEmailAttribute(String emailAttribute) {
        this.emailAttribute = emailAttribute;
    }

    public String getMetadataAttribute() {
        return FIELD_METADATA;
    }

    public boolean getAutoProvisioning() {
        return autoProvisioning;
    }

    public void setAutoProvisioning(boolean autoProvisioning) {
        this.autoProvisioning = autoProvisioning;
    }

    public PasswordEncoderOptions getPasswordEncoderOptions() {
        return passwordEncoderOptions;
    }

    public void setPasswordEncoderOptions(PasswordEncoderOptions passwordEncoderOptions) {
        this.passwordEncoderOptions = passwordEncoderOptions;
    }
}
