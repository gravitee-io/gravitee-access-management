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
package io.gravitee.am.service.repository;

import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class MemoryRepository<T, ID> implements CrudRepository<T, ID> {
    private final ConcurrentMap<ID, T> storage;

    public MemoryRepository() {
        this(ConcurrentHashMap::new);
    }

    public MemoryRepository(Supplier<ConcurrentMap<ID, T>> mapConstructor) {
        storage = mapConstructor.get();

    }

    protected abstract ID getId(T item);

    /**
     * Generate an id for item and set it,
     * This method is expected to mutate the item.
     * @return the generated id
     */
    protected abstract ID generateAndSetId(T item);

    protected Single<T> findOne(Predicate<T> predicate) {
        return Single.just(storage.values().stream().filter(predicate).findFirst().get());
    }

    protected Flowable<T> findMany(Predicate<T> predicate) {
        return Flowable.fromStream(storage.values().stream().filter(predicate));
    }

    protected Flowable<T> allValues() {
        return Flowable.fromIterable(storage.values());
    }

    @Override
    public Maybe<T> findById(ID id) {
        return Maybe.fromOptional(Optional.ofNullable(storage.get(id)));
    }

    @Override
    public Single<T> create(T item) {
        var id = Objects.requireNonNullElseGet(getId(item), () -> generateAndSetId(item));

        storage.put(id, item);
        return Single.just(item);

    }

    @Override
    public Single<T> update(T item) {
        var id = getId(item);
        storage.put(id, item);
        return Single.just(item);

    }

    @Override
    public Completable delete(ID id) {
        storage.remove(id);
        return Completable.complete();
    }
}
