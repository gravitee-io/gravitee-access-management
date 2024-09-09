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
package io.gravitee.am.repository.junit.management;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.repository.junit.MemoryRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.reactivex.rxjava3.core.Flowable;

import java.util.UUID;

public class MemoryReporterRepository extends MemoryRepository<Reporter,String> implements ReporterRepository {
    @Override
    public Flowable<Reporter> findAll() {
        return allValues();
    }

    @Override
    public Flowable<Reporter> findByReference(Reference reference) {
        return findMany(x->x.getReference().equals(reference));
    }

    @Override
    public Flowable<Reporter> findInheritedFrom(Reference parentReference) {
        return findByReference(parentReference)
                .filter(Reporter::isInherited);
    }

    @Override
    protected String getId(Reporter item) {
        return item.getId();
    }

    @Override
    protected String generateAndSetId(Reporter item) {
        var id = UUID.randomUUID().toString();
        item.setId(id);
        return id;
    }
}
