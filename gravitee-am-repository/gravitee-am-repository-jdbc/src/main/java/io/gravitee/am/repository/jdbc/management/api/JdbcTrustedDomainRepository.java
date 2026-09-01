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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.jose.JWKModule;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServer;
import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain.JdbcSpiffe;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain.JdbcTokenExchange;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain.JdbcXaa;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain.JdbcXaaResourceServer;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringTrustedDomainRepository;
import io.gravitee.am.repository.management.api.TrustedDomainRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain.FIELD_TRUSTED_DOMAIN_ID;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

@Repository
public class JdbcTrustedDomainRepository extends AbstractJdbcRepository implements TrustedDomainRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JWKModule());
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final TypeReference<List<UserBindingCriterion>> CRITERION_LIST = new TypeReference<>() {};
    private static final TypeReference<TrustDomainKeyMaterial> KEY_MATERIAL = new TypeReference<>() {};

    @Autowired
    private SpringTrustedDomainRepository repository;

    @Override
    public Maybe<TrustedDomain> findById(String id) {
        LOGGER.debug("findById({})", id);
        return repository.findById(id)
                .map(this::toEntity)
                .flatMap(td -> complete(td).toMaybe())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustedDomain> create(TrustedDomain item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create trusted domain with id {}", item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> insert = getTemplate().insert(toJdbcEntity(item))
                .then(persistUsages(item));
        return monoToCompletable(insert.as(trx::transactional))
                .andThen(findById(item.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustedDomain> update(TrustedDomain item) {
        LOGGER.debug("Update trusted domain with id {}", item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> update = getTemplate().update(toJdbcEntity(item))
                .then(deleteUsages(item.getId()))
                .then(persistUsages(item));
        return monoToCompletable(update.as(trx::transactional))
                .andThen(findById(item.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> delete = deleteUsages(id)
                .then(getTemplate().delete(query(where("id").is(id)), JdbcTrustedDomain.class));
        return monoToCompletable(delete.as(trx::transactional))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<TrustedDomain> findByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({}, {})", referenceType, referenceId);
        return repository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity)
                .concatMapSingle(this::complete)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustedDomain> findByName(ReferenceType referenceType, String referenceId, String name) {
        LOGGER.debug("findByName({}, {}, {})", referenceType, referenceId, name);
        return repository.findByName(referenceType.name(), referenceId, name)
                .map(this::toEntity)
                .flatMap(td -> complete(td).toMaybe())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustedDomain> findBySpiffeTrustDomain(ReferenceType referenceType, String referenceId, String spiffeTrustDomain) {
        LOGGER.debug("findBySpiffeTrustDomain({}, {}, {})", referenceType, referenceId, spiffeTrustDomain);
        return repository.findBySpiffeTrustDomain(referenceType.name(), referenceId, spiffeTrustDomain)
                .map(this::toEntity)
                .flatMap(td -> complete(td).toMaybe())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustedDomain> findByIssuer(ReferenceType referenceType, String referenceId, String issuer) {
        LOGGER.debug("findByIssuer({}, {}, {})", referenceType, referenceId, issuer);
        return repository.findByDomainIdentifier(referenceType.name(), referenceId, issuer)
                .map(this::toEntity)
                .flatMap(td -> complete(td).toMaybe())
                .observeOn(Schedulers.computation());
    }

    private Single<TrustedDomain> complete(TrustedDomain td) {
        Mono<TrustedDomain> flow = selectUsage(JdbcSpiffe.class, td.getId())
                .doOnNext(row -> td.setSpiffe(toSpiffe(row)))
                .thenReturn(td)
                .flatMap(loaded -> selectUsage(JdbcTokenExchange.class, td.getId())
                        .doOnNext(row -> loaded.setTokenExchange(toTokenExchange(row)))
                        .thenReturn(loaded))
                .flatMap(loaded -> selectUsage(JdbcXaa.class, td.getId())
                        .doOnNext(row -> loaded.setCrossAppAccess(toCrossAppAccess(row)))
                        .thenReturn(loaded))
                .flatMap(this::loadResourceServers);
        return monoToSingle(flow);
    }

    private Mono<TrustedDomain> loadResourceServers(TrustedDomain td) {
        if (td.getCrossAppAccess() == null) {
            return Mono.just(td);
        }
        return getTemplate().select(JdbcXaaResourceServer.class)
                .matching(query(where(FIELD_TRUSTED_DOMAIN_ID).is(td.getId())).sort(Sort.by("name")))
                .all()
                .map(JdbcTrustedDomainRepository::toResourceServer)
                .collectList()
                .doOnNext(resourceServers -> {
                    if (!resourceServers.isEmpty()) {
                        td.getCrossAppAccess().setResourceServers(resourceServers);
                    }
                })
                .thenReturn(td);
    }

    private <T> Mono<T> selectUsage(Class<T> type, String trustedDomainId) {
        return getTemplate().select(type)
                .matching(query(where(FIELD_TRUSTED_DOMAIN_ID).is(trustedDomainId)))
                .one();
    }

    private Mono<Void> persistUsages(TrustedDomain td) {
        return Mono.justOrEmpty(toJdbcSpiffe(td)).flatMap(getTemplate()::insert)
                .then(Mono.justOrEmpty(toJdbcTokenExchange(td)).flatMap(getTemplate()::insert))
                .then(Mono.justOrEmpty(toJdbcXaa(td)).flatMap(getTemplate()::insert))
                .then(persistResourceServers(td));
    }

    private Mono<Void> persistResourceServers(TrustedDomain td) {
        List<CrossAppAccessResourceServer> resourceServers = td.crossAppAccessResourceServers();
        if (resourceServers.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(resourceServers)
                .concatMap(resourceServer -> getTemplate().insert(toJdbcResourceServer(td.getId(), resourceServer)))
                .then();
    }

    private Mono<Void> deleteUsages(String trustedDomainId) {
        return deleteUsage(JdbcSpiffe.class, trustedDomainId)
                .then(deleteUsage(JdbcTokenExchange.class, trustedDomainId))
                .then(deleteUsage(JdbcXaa.class, trustedDomainId))
                .then(deleteUsage(JdbcXaaResourceServer.class, trustedDomainId))
                .then();
    }

    private Mono<Long> deleteUsage(Class<?> type, String trustedDomainId) {
        return getTemplate().delete(query(where(FIELD_TRUSTED_DOMAIN_ID).is(trustedDomainId)), type);
    }

    private TrustedDomain toEntity(JdbcTrustedDomain entity) {
        if (entity == null) {
            return null;
        }
        TrustedDomain td = new TrustedDomain();
        td.setId(entity.getId());
        td.setReferenceId(entity.getReferenceId());
        td.setReferenceType(entity.getReferenceType() != null ? ReferenceType.valueOf(entity.getReferenceType()) : null);
        td.setName(entity.getName());
        td.setDescription(entity.getDescription());
        td.setDomainIdentifier(entity.getDomainIdentifier());
        td.setKeyMaterial(parseJson(entity.getKeyMaterial(), KEY_MATERIAL, "key material"));
        td.setCreatedAt(toDate(entity.getCreatedAt()));
        td.setUpdatedAt(toDate(entity.getUpdatedAt()));
        return td;
    }

    private JdbcTrustedDomain toJdbcEntity(TrustedDomain td) {
        if (td == null) {
            return null;
        }
        JdbcTrustedDomain entity = new JdbcTrustedDomain();
        entity.setId(td.getId());
        entity.setReferenceId(td.getReferenceId());
        entity.setReferenceType(td.getReferenceType() != null ? td.getReferenceType().name() : null);
        entity.setName(td.getName());
        entity.setDescription(td.getDescription());
        entity.setDomainIdentifier(td.getDomainIdentifier());
        entity.setKeyMaterial(serializeJson(td.getKeyMaterial(), "key material"));
        entity.setCreatedAt(toLocalDateTime(td.getCreatedAt()));
        entity.setUpdatedAt(toLocalDateTime(td.getUpdatedAt()));
        return entity;
    }

    private static SpiffeTrustSettings toSpiffe(JdbcSpiffe row) {
        return SpiffeTrustSettings.builder()
                .spiffeTrustDomain(row.getSpiffeTrustDomain())
                .allowedAlgorithms(parseJson(row.getAllowedAlgorithms(), STRING_LIST, "allowed algorithms"))
                .build();
    }

    private static JdbcSpiffe toJdbcSpiffe(TrustedDomain td) {
        if (td.getSpiffe() == null) {
            return null;
        }
        JdbcSpiffe row = new JdbcSpiffe();
        row.setTrustedDomainId(td.getId());
        row.setReferenceId(td.getReferenceId());
        row.setReferenceType(td.getReferenceType() != null ? td.getReferenceType().name() : null);
        row.setSpiffeTrustDomain(td.getSpiffeTrustDomain());
        row.setAllowedAlgorithms(serializeJson(td.getAllowedAlgorithms(), "allowed algorithms"));
        return row;
    }

    private static TokenExchangeTrustSettings toTokenExchange(JdbcTokenExchange row) {
        return TokenExchangeTrustSettings.builder()
                .enabled(row.isEnabled())
                .scopeMappings(parseJson(row.getScopeMappings(), STRING_MAP, "scope mappings"))
                .userBindingEnabled(row.isUserBindingEnabled())
                .userBindingCriteria(parseJson(row.getUserBindingCriteria(), CRITERION_LIST, "user binding criteria"))
                .build();
    }

    private static JdbcTokenExchange toJdbcTokenExchange(TrustedDomain td) {
        TokenExchangeTrustSettings tokenExchange = td.getTokenExchange();
        if (tokenExchange == null) {
            return null;
        }
        JdbcTokenExchange row = new JdbcTokenExchange();
        row.setTrustedDomainId(td.getId());
        row.setEnabled(td.trustsTokenExchange());
        row.setScopeMappings(serializeJson(tokenExchange.getScopeMappings(), "scope mappings"));
        row.setUserBindingEnabled(tokenExchange.isUserBindingEnabled());
        row.setUserBindingCriteria(serializeJson(tokenExchange.getUserBindingCriteria(), "user binding criteria"));
        return row;
    }

    private static CrossAppAccessSettings toCrossAppAccess(JdbcXaa row) {
        return CrossAppAccessSettings.builder()
                .enabled(row.isEnabled())
                .audSubMapping(row.getAudSubMapping())
                .scopeMappings(parseJson(row.getScopeMappings(), STRING_MAP, "cross app access scope mappings"))
                .build();
    }

    private static JdbcXaa toJdbcXaa(TrustedDomain td) {
        CrossAppAccessSettings crossAppAccess = td.getCrossAppAccess();
        if (crossAppAccess == null) {
            return null;
        }
        JdbcXaa row = new JdbcXaa();
        row.setTrustedDomainId(td.getId());
        row.setEnabled(crossAppAccess.isEnabled());
        row.setAudSubMapping(crossAppAccess.getAudSubMapping());
        row.setScopeMappings(serializeJson(crossAppAccess.getScopeMappings(), "cross app access scope mappings"));
        return row;
    }

    private static CrossAppAccessResourceServer toResourceServer(JdbcXaaResourceServer row) {
        return CrossAppAccessResourceServer.builder()
                .id(row.getId())
                .name(row.getName())
                .resource(row.getResource())
                .build();
    }

    private static JdbcXaaResourceServer toJdbcResourceServer(String trustedDomainId, CrossAppAccessResourceServer resourceServer) {
        JdbcXaaResourceServer row = new JdbcXaaResourceServer();
        row.setId(resourceServer.getId() == null ? RandomString.generate() : resourceServer.getId());
        row.setTrustedDomainId(trustedDomainId);
        row.setName(resourceServer.getName());
        row.setResource(resourceServer.getResource());
        return row;
    }

    private static <T> T parseJson(String json, TypeReference<T> type, String what) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse trusted domain " + what, e);
        }
    }

    private static String serializeJson(Object value, String what) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trusted domain " + what, e);
        }
    }

}
