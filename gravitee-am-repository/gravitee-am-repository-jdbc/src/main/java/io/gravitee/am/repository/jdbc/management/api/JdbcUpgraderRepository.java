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
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcUpgrader;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringUpgraderRepository;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcUpgraderRepository extends AbstractJdbcRepository implements UpgraderRepository {

    @Autowired
    private SpringUpgraderRepository upgraderRepository;

    protected UpgradeRecord toEntity(JdbcUpgrader entity) {
        return mapper.map(entity, UpgradeRecord.class);
    }

    protected JdbcUpgrader toJdbcEntity(UpgradeRecord entity) {
        return mapper.map(entity, JdbcUpgrader.class);
    }

    @Override
    public Maybe<UpgradeRecord> findById(String id) {
        LOGGER.debug("findById({})", id);
        return upgraderRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<UpgradeRecord> create(UpgradeRecord item) {
        LOGGER.debug("Create upgrader record with id {}", item.getId());
        return monoToSingle(template.insert(toJdbcEntity(item))).map(this::toEntity);
    }

}
