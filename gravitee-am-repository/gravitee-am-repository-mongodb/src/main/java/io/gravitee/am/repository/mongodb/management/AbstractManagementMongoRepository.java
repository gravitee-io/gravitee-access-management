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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import io.reactivex.Maybe;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractManagementMongoRepository extends AbstractMongoRepository {

    protected static final String FIELD_REFERENCE_TYPE = "referenceType";
    protected static final String FIELD_REFERENCE_ID = "referenceId";

    @Autowired
    @Qualifier("managementMongoTemplate")
    protected MongoDatabase mongoOperations;


    protected Bson toBsonFilter(String name, Optional<?> optional) {

        return optional.map(value -> {
            if (value instanceof Enum) {
                return eq(name, ((Enum<?>) value).name());
            }

            return eq(name, value);
        }).orElse(null);
    }

    protected Maybe<Bson> toBsonFilter(boolean logicalOr, Bson... filter) {

        List<Bson> filterCriteria = Stream.of(filter).filter(Objects::nonNull).collect(Collectors.toList());

        if (filterCriteria.isEmpty()) {
            return Maybe.empty();
        }

        Bson bsonFilter;

        if (logicalOr) {
            return Maybe.just(or(filterCriteria));
        } else {
            return Maybe.just(and(filterCriteria));
        }
    }
}
