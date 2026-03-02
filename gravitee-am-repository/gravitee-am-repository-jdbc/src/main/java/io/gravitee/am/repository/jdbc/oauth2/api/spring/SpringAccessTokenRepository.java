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
package io.gravitee.am.repository.jdbc.oauth2.api.spring;

import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcAccessToken;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public interface SpringAccessTokenRepository extends RxJava3CrudRepository<JdbcAccessToken, String> {

    @Query("select * from access_tokens a where a.token = :token and (a.expire_at > :now or a.expire_at is null)")
    Maybe<JdbcAccessToken> findByToken(@Param("token") String token, @Param("now")LocalDateTime now);

    @Query("select * from access_tokens a where a.authorization_code = :auth and (a.expire_at > :now or a.expire_at is null)")
    Flowable<JdbcAccessToken> findByAuthorizationCode(@Param("auth") String code, @Param("now")LocalDateTime now);

}
