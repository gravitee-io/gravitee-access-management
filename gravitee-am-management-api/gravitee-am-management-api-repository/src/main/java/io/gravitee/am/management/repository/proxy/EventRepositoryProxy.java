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
package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.EventRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EventRepositoryProxy extends AbstractProxy<EventRepository> implements EventRepository  {

    @Override
    public Single<List<Event>> findByTimeFrame(long from, long to) {
        return target.findByTimeFrame(from, to);
    }

    @Override
    public Maybe<Event> findById(String s) {
        return target.findById(s);
    }

    @Override
    public Single<Event> create(Event item) {
        return target.create(item);
    }

    @Override
    public Single<Event> update(Event item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String s) {
        return target.delete(s);
    }
}
