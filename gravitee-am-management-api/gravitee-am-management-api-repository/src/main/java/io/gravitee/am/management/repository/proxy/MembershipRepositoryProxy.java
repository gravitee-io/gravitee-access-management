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

import io.gravitee.am.model.Membership;
import io.gravitee.am.model.membership.ReferenceType;
import io.gravitee.am.repository.management.api.MembershipRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MembershipRepositoryProxy extends AbstractProxy<MembershipRepository> implements MembershipRepository {

    @Override
    public Flowable<Membership> findAll() {
        return target.findAll();
    }

    @Override
    public Single<List<Membership>> findByReference(String referenceId, ReferenceType referenceType) {
        return target.findByReference(referenceId, referenceType);
    }

    @Override
    public Maybe<Membership> findByReferenceAndMember(String referenceId, String memberId) {
        return target.findByReferenceAndMember(referenceId, memberId);
    }

    @Override
    public Maybe<Membership> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<Membership> create(Membership item) {
        return target.create(item);
    }

    @Override
    public Single<Membership> update(Membership item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
