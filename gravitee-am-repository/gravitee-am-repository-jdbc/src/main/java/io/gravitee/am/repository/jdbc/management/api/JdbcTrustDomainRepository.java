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
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.model.oidc.TrustDomainTokenExchangeSettings;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustDomain;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringTrustDomainRepository;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

@Repository
public class JdbcTrustDomainRepository extends AbstractJdbcRepository implements TrustDomainRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JWKModule());
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final TypeReference<List<UserBindingCriterion>> CRITERION_LIST = new TypeReference<>() {};
    private static final TypeReference<TrustDomainKeyMaterial> KEY_MATERIAL = new TypeReference<>() {};

    @Autowired
    private SpringTrustDomainRepository repository;

    @Override
    public Maybe<TrustDomain> findById(String id) {
        LOGGER.debug("findById({})", id);
        return repository.findById(id)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustDomain> create(TrustDomain item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create trust domain with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item)))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustDomain> update(TrustDomain item) {
        LOGGER.debug("Update trust domain with id {}", item.getId());
        return repository.save(toJdbcEntity(item))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return repository.deleteById(id)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<TrustDomain> findByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({}, {})", referenceType, referenceId);
        return repository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustDomain> findByName(ReferenceType referenceType, String referenceId, TrustDomainKind kind, String name) {
        LOGGER.debug("findByName({}, {}, {}, {})", referenceType, referenceId, kind, name);
        return repository.findByName(referenceType.name(), referenceId, kind.name(), name)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustDomain> findByIssuer(ReferenceType referenceType, String referenceId, String issuer) {
        LOGGER.debug("findByIssuer({}, {}, {})", referenceType, referenceId, issuer);
        return repository.findByIssuer(referenceType.name(), referenceId, issuer)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    private TrustDomain toEntity(JdbcTrustDomain entity) {
        if (entity == null) {
            return null;
        }
        TrustDomain td = new TrustDomain();
        td.setId(entity.getId());
        td.setReferenceId(entity.getReferenceId());
        td.setReferenceType(entity.getReferenceType() != null ? ReferenceType.valueOf(entity.getReferenceType()) : null);
        td.setKind(TrustDomainKind.fromStored(entity.getKind()));
        td.setName(entity.getName());
        td.setDescription(entity.getDescription());
        td.setKeyMaterial(readKeyMaterial(entity));
        td.setRefreshIntervalSeconds(entity.getRefreshIntervalSeconds());
        td.setAllowedAlgorithms(parseJson(entity.getAllowedAlgorithms(), STRING_LIST, "allowed algorithms"));
        td.setTokenExchange(readTokenExchange(entity));
        td.setCreatedAt(toDate(entity.getCreatedAt()));
        td.setUpdatedAt(toDate(entity.getUpdatedAt()));
        return td;
    }

    private JdbcTrustDomain toJdbcEntity(TrustDomain td) {
        if (td == null) {
            return null;
        }
        JdbcTrustDomain entity = new JdbcTrustDomain();
        entity.setId(td.getId());
        entity.setReferenceId(td.getReferenceId());
        entity.setReferenceType(td.getReferenceType() != null ? td.getReferenceType().name() : null);
        entity.setKind(td.getKind() != null ? td.getKind().name() : TrustDomainKind.SPIFFE.name());
        entity.setName(td.getName());
        entity.setDescription(td.getDescription());
        entity.setKeyMaterial(serializeJson(td.getKeyMaterial(), "key material"));
        entity.setRefreshIntervalSeconds(td.getRefreshIntervalSeconds());
        entity.setAllowedAlgorithms(serializeJson(td.getAllowedAlgorithms(), "allowed algorithms"));
        writeTokenExchange(entity, td.getTokenExchange());
        entity.setCreatedAt(toLocalDateTime(td.getCreatedAt()));
        entity.setUpdatedAt(toLocalDateTime(td.getUpdatedAt()));
        return entity;
    }

    /**
     * Reads the shared key-material shape, falling back to the legacy bundle-source columns for
     * trust domains stored before it existed.
     */
    static TrustDomainKeyMaterial readKeyMaterial(JdbcTrustDomain entity) {
        if (entity.getKeyMaterial() != null && !entity.getKeyMaterial().isBlank()) {
            return parseJson(entity.getKeyMaterial(), KEY_MATERIAL, "key material");
        }
        return TrustDomainKeyMaterial.fromBundleSource(
                entity.getBundleSource() != null ? SpiffeBundleSource.valueOf(entity.getBundleSource()) : null,
                entity.getJwksUrl());
    }

    private static TrustDomainTokenExchangeSettings readTokenExchange(JdbcTrustDomain entity) {
        if (entity.getIssuer() == null) {
            return null;
        }
        return TrustDomainTokenExchangeSettings.builder()
                .issuer(entity.getIssuer())
                .scopeMappings(parseJson(entity.getScopeMappings(), STRING_MAP, "scope mappings"))
                .userBindingEnabled(Boolean.TRUE.equals(entity.getUserBindingEnabled()))
                .userBindingCriteria(parseJson(entity.getUserBindingCriteria(), CRITERION_LIST, "user binding criteria"))
                .build();
    }

    private static void writeTokenExchange(JdbcTrustDomain entity, TrustDomainTokenExchangeSettings settings) {
        if (settings == null) {
            return;
        }
        entity.setIssuer(settings.getIssuer());
        entity.setScopeMappings(serializeJson(settings.getScopeMappings(), "scope mappings"));
        entity.setUserBindingEnabled(settings.isUserBindingEnabled());
        entity.setUserBindingCriteria(serializeJson(settings.getUserBindingCriteria(), "user binding criteria"));
    }

    private static <T> T parseJson(String json, TypeReference<T> type, String what) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse trust domain " + what, e);
        }
    }

    private static String serializeJson(Object value, String what) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trust domain " + what, e);
        }
    }

}
