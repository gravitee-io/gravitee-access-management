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

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.jdbc.authentication.JdbcAuthenticationProvider;
import io.gravitee.am.identityprovider.jdbc.user.JdbcUserProvider;
import io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCConnectionProvider;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCPoolWrapper;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Database/version selection is driven by the {@code jdbcType} system property.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class JdbcAuthenticationProviderConfigurationTest implements InitializingBean {

    private static final String DEFAULT_JDBC_TYPE = "postgresql-tc~18.4";

    @Autowired
    private ConnectionPool connectionPool;

    private String jdbcType() {
        return System.getProperty("jdbcType", DEFAULT_JDBC_TYPE);
    }

    private String version(String fallback) {
        final String jdbcType = jdbcType();
        return jdbcType.contains("~") ? jdbcType.split("~")[1] : fallback;
    }

    public String protocol() {
        final String jdbcType = jdbcType();
        if (jdbcType.startsWith("mssql-tc")) {
            return "sqlserver";
        }
        if (jdbcType.startsWith("mysql-tc")) {
            return "mysql";
        }
        if (jdbcType.startsWith("mariadb-tc")) {
            return "mariadb";
        }
        return "postgresql";
    }

    public String url() {
        final String jdbcType = jdbcType();
        if (jdbcType.startsWith("mssql-tc")) {
            return "r2dbc:tc:sqlserver:///?TC_IMAGE_TAG=" + version("2022-latest") + "&preferCursoredExecution=false";
        }
        if (jdbcType.startsWith("mysql-tc")) {
            return "r2dbc:tc:mysql:///databasename?TC_IMAGE_TAG=" + version("8.4");
        }
        if (jdbcType.startsWith("mariadb-tc")) {
            return "r2dbc:tc:mariadb:///databasename?TC_IMAGE_TAG=" + version("11.6.2");
        }
        return "r2dbc:tc:postgresql:///databasename?TC_IMAGE_TAG=" + version("18.4");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // create table users and insert values
        Connection connection = connectionPool.create().block();
        initData(connection);
        Completable.fromPublisher(connection.close()).subscribe();
    }

    @Component
    public class TestContainerR2DBCConnectionProvider extends R2DBCConnectionProvider {
        @Override
        public void afterPropertiesSet() throws Exception {
            // nothing to implement here for TestContainer tests
        }

        @Override
        public ClientWrapper getClientWrapper(String name) {
            return new R2DBCPoolWrapper(null, connectionPool);
        }
    }

    protected void initData(Connection connection) {
        if ("sqlserver".equals(protocol())) {
            initMssqlData(connection);
            return;
        }
        Single.fromPublisher(connection.createStatement("create table users(id varchar(256), username varchar(256) unique, password varchar(256), email varchar(256), metadata text)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('1', 'bob', 'bobspassword', null, null)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('2', 'user01', 'user01', 'user01@acme.com', null)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('3', 'user02', 'user02', 'common@acme.com', null)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('4', 'user03', 'user03', 'common@acme.com', null)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('5', 'changeme', 'changepass', null, null)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('6', 'b o b', 'changepass', null, null)").execute()).blockingGet();
    }

    /**
     * MSSQL needs named-parameter binds for the seed inserts (its R2DBC driver doesn't accept the same
     * positional-literal statement syntax used for the other dialects).
     */
    private void initMssqlData(Connection connection) {
        Single.fromPublisher(connection.createStatement("create table users(id varchar(256), username varchar(256) unique, password varchar(256), email varchar(256), metadata text)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users values('1', 'bob', 'bobspassword', null, null)").execute()).blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users(id, username, password, email, metadata) values( @id, @username, @password, @email , @metadata)")
                .bind("id", "2")
                .bind("username", "user01")
                .bind("password", "user01")
                .bind("email", "user01@acme.com")
                .bindNull("metadata", String.class)
                .execute()).flatMap(rp -> Single.fromPublisher(rp.getRowsUpdated()))
                .blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users(id, username, password, email, metadata) values( @id, @username, @password, @email , @metadata)")
                .bind("id", "3")
                .bind("username", "user02")
                .bind("password", "user02")
                .bind("email", "common@acme.com")
                .bindNull("metadata", String.class)
                .execute()).flatMap(rp -> Single.fromPublisher(rp.getRowsUpdated()))
                .blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users(id, username, password, email, metadata) values( @id, @username, @password, @email , @metadata)")
                .bind("id", "4")
                .bind("username", "user03")
                .bind("password", "user03")
                .bind("email", "common@acme.com")
                .bindNull("metadata", String.class)
                .execute()).flatMap(rp -> Single.fromPublisher(rp.getRowsUpdated()))
                .blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users(id, username, password, email, metadata) values( @id, @username, @password, @email , @metadata)")
                        .bind("id", "5")
                        .bind("username", "changeme")
                        .bind("password", "changepass")
                        .bindNull("email", String.class)
                        .bindNull("metadata", String.class)
                        .execute()).flatMap(rp -> Single.fromPublisher(rp.getRowsUpdated()))
                .blockingGet();
        Single.fromPublisher(connection.createStatement("insert into users(id, username, password, email, metadata) values( @id, @username, @password, @email , @metadata)")
                        .bind("id", "6")
                        .bind("username", "b o b")
                        .bind("password", "changepass")
                        .bindNull("email", String.class)
                        .bindNull("metadata", String.class)
                        .execute()).flatMap(rp -> Single.fromPublisher(rp.getRowsUpdated()))
                .blockingGet();
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
    public IdentityProvider identprovider() {
        return new IdentityProvider();
    }

    @Bean
    public IdentityProviderMapper mapper() {
        return new DefaultIdentityProviderMapper();
    }

    @Bean
    public IdentityProviderRoleMapper roleMapper() {
        return new DefaultIdentityProviderRoleMapper();
    }

    @Bean
    public IdentityProviderGroupMapper groupMapper() {
        return new DefaultIdentityProviderGroupMapper();
    }

    @Bean
    public RepositoriesEnvironment repositoriesEnvironment(Environment environment) {
        return new RepositoriesEnvironment(environment);
    }
}
