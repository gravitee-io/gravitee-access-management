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

import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.jdbc.authentication.JdbcAuthenticationProvider;
import io.gravitee.am.identityprovider.jdbc.user.JdbcUserProvider;
import io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class JdbcAuthenticationProviderConfigurationTest implements InitializingBean {

    @Autowired
    private ConnectionPool connectionPool;

    public abstract String url();

    public abstract String protocol();

    @Override
    public void afterPropertiesSet() throws Exception {
        // create table users and insert values
        Connection connection = connectionPool.create().block();
        initData(connection);
        Completable.fromPublisher(connection.close()).subscribe();
    }

    protected void initData(Connection connection) {
        Single.fromPublisher(connection.createStatement("create table users(id varchar(256), username varchar(256), password varchar(256), email varchar(256), metadata text)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('1', 'bob', 'bobspassword', null, null)").execute()).subscribe();
        Single.fromPublisher(connection.createStatement("insert into users values('2', 'user01', 'user01', 'user01@acme.com', null)").execute()).subscribe();
    }

    @Bean
    public ConnectionPool connectionPool() {
        ConnectionFactory connectionFactory = ConnectionFactories.get(url());

        ConnectionPoolConfiguration connectionPoolConfiguration = ConnectionPoolConfiguration
                .builder(connectionFactory)
                .build();

        return new ConnectionPool(connectionPoolConfiguration);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        JdbcAuthenticationProvider jdbcAuthenticationProvider = new JdbcAuthenticationProvider();
        jdbcAuthenticationProvider.setConnectionPool(connectionPool);
        return jdbcAuthenticationProvider;
    }

    @Bean
    public UserProvider userProvider() {
        JdbcUserProvider jdbcUserProvider = new JdbcUserProvider();
        jdbcUserProvider.setConnectionPool(connectionPool);
        return jdbcUserProvider;
    }

    @Bean
    public JdbcIdentityProviderConfiguration configuration() {
        JdbcIdentityProviderConfiguration configuration = new JdbcIdentityProviderConfiguration();
        configuration.setProtocol(protocol());
        configuration.setUsersTable("users");
        configuration.setSelectUserByUsernameQuery("select * from users where username = %s");
        configuration.setSelectUserByEmailQuery("select * from users where email = %s");
        configuration.setSelectUserByMultipleFieldsQuery("select * from users where username = %s or email = %s ");
        configuration.setPasswordEncoder(PasswordEncoder.NONE);

        return configuration;
    }

    @Bean
    public IdentityProviderMapper mapper() {
        return new DefaultIdentityProviderMapper();
    }

    @Bean
    public IdentityProviderRoleMapper roleMapper() {
        return new DefaultIdentityProviderRoleMapper();
    }
}
