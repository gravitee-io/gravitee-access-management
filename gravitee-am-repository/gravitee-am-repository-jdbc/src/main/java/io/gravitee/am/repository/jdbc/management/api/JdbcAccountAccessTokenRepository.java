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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAccountAccessToken;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAccountAccessTokenRepository;
import io.gravitee.am.repository.management.api.AccountAccessTokenRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcAccountAccessTokenRepository extends AbstractJdbcRepository implements AccountAccessTokenRepository {

    private final SpringAccountAccessTokenRepository accessTokenRepository;

    @Override
    public Maybe<AccountAccessToken> findById(String tokenId) {
        return accessTokenRepository.findById(tokenId)
                .map(this::fromEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AccountAccessToken> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return accessTokenRepository.findByUserId(referenceType.name(), referenceId, userId).map(this::fromEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(ReferenceType referenceType, String referenceId, String userId) {
        return accessTokenRepository.deleteByUserId(referenceType.name(), referenceId, userId).ignoreElement()
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AccountAccessToken> create(AccountAccessToken item) {
        var entity = toEntity(item);
        if (entity.getId() == null) {
            entity.setId(RandomString.generate());
        }
        LOGGER.debug("Creating account access token with id {}", entity.getId());
        return Single.fromPublisher(getTemplate().insert(entity).map(this::fromEntity))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AccountAccessToken> update(AccountAccessToken item) {
        LOGGER.debug("Updating account access token with id {}", item.tokenId());
        var entity = toEntity(item);
        return accessTokenRepository.save(entity).map(this::fromEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String tokenId) {
        return accessTokenRepository.deleteById(tokenId)
                .observeOn(Schedulers.computation());
    }

    private AccountAccessToken fromEntity(JdbcAccountAccessToken entity) {
        return AccountAccessToken.builder()
                .tokenId(entity.getId())
                .referenceType(ReferenceType.valueOf(entity.getReferenceType()))
                .referenceId(entity.getReferenceId())
                .userId(entity.getUserId())
                .issuerId(entity.getIssuerId())
                .name(entity.getName())
                .token(entity.getToken())
                .createdAt(toDate(entity.getCreatedAt()))
                .build();
    }

    private JdbcAccountAccessToken toEntity(AccountAccessToken token) {
        var dbToken = JdbcAccountAccessToken.builder()
                .id(token.tokenId())
                .referenceType(token.referenceType().name())
                .referenceId(token.referenceId())
                .userId(token.userId())
                .issuerId(token.issuerId())
                .name(token.name())
                .token(token.token())
                .build();
        var now = LocalDateTime.now();
        dbToken.setCreatedAt(Optional.ofNullable(token.createdAt())
                .map(this::toLocalDateTime)
                .orElse(now));
        dbToken.setUpdatedAt(now);
        return dbToken;
    }

}
