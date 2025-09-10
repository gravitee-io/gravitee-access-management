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
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoderOptions;
import lombok.Getter;
import lombok.Setter;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */

@Getter
@Setter
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
    private String datasource;

    @Override
    public boolean userProvider() {
        return this.userProvider;
    }

}
