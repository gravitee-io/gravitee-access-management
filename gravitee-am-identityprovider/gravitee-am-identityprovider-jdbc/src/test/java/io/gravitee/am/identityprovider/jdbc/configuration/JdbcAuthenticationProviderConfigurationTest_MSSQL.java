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

import io.r2dbc.spi.Connection;
import io.reactivex.rxjava3.core.Single;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class JdbcAuthenticationProviderConfigurationTest_MSSQL extends JdbcAuthenticationProviderConfigurationTest {

    public String url() {
        return "r2dbc:tc:sqlserver:///?TC_IMAGE_TAG=2019-latest";
    }

    @Override
    public String protocol() {
        return "sqlserver";
    }

    @Override
    protected void initData(Connection connection) {
        Single.fromPublisher(connection.createStatement("create table users(id varchar(256), username varchar(256), password varchar(256), email varchar(256), metadata text)").execute()).blockingGet();
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
}
