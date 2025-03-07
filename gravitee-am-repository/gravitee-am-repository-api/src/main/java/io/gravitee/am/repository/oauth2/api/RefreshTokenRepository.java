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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface RefreshTokenRepository extends ExpiredDataSweeper {

    Maybe<RefreshToken> findByToken(String token);

    Single<RefreshToken> create(RefreshToken refreshToken);

    Completable delete(String token);

    Completable deleteByUserId(String userId);

    Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId);

    Completable deleteByDomainIdAndUserId(String domainId, UserId userId);

    CompletableSource deleteByDomainIdAndClientId(String domainId, String clientId);

}
