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

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.jdbc.JdbcIdentityProviderMapper;
import io.gravitee.am.identityprovider.jdbc.JdbcIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.jdbc.authentication.JdbcAuthenticationProvider;
import io.gravitee.am.identityprovider.jdbc.user.JdbcUserProvider;
import io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder;
import org.davidmoten.rx.jdbc.Database;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class JdbcAuthenticationProviderConfigurationTest implements InitializingBean {

    @Autowired
    private Database db;

    @Override
    public void afterPropertiesSet() throws Exception {
        // create table users and insert values
        db.update("create table users(id varchar(256), username varchar(256), password varchar(256), email varchar(256), metadata varchar(256))")
                .counts()
                .ignoreElements()
                .blockingGet();
        db.update("insert into users values('1', 'bob', 'bobspassword', null, null)")
                .counts()
                .ignoreElements()
                .blockingGet();
    }

    @Bean
    public Database database() {
        return Database.test();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        JdbcAuthenticationProvider jdbcAuthenticationProvider = new JdbcAuthenticationProvider();
        jdbcAuthenticationProvider.setDb(db);
        return jdbcAuthenticationProvider;
    }

    @Bean
    public UserProvider userProvider() {
        JdbcUserProvider jdbcUserProvider = new JdbcUserProvider();
        jdbcUserProvider.setDb(db);
        return jdbcUserProvider;
    }

    @Bean
    public JdbcIdentityProviderConfiguration configuration() {
        JdbcIdentityProviderConfiguration configuration = new JdbcIdentityProviderConfiguration();
        configuration.setUsersTable("users");
        configuration.setIdentifierAttribute("ID");
        configuration.setUsernameAttribute("USERNAME");
        configuration.setPasswordAttribute("PASSWORD");
        configuration.setSelectUserByUsernameQuery("select * from users where username = %s");
        configuration.setPasswordEncoder(PasswordEncoder.NONE.getValue());

        return configuration;
    }

    @Bean
    public JdbcIdentityProviderMapper mapper() {
        return new JdbcIdentityProviderMapper();
    }

    @Bean
    public JdbcIdentityProviderRoleMapper roleMapper() {
        return new JdbcIdentityProviderRoleMapper();
    }
}
