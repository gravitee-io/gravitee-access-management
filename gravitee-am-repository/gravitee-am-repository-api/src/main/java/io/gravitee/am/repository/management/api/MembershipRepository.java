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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface MembershipRepository extends CrudRepository<Membership, String> {

    Flowable<Membership> findByReference(String referenceId, ReferenceType referenceType);

    Flowable<Membership> findByMember(String memberId, MemberType memberType);

    Flowable<Membership> findByCriteria(ReferenceType referenceType, String referenceId, MembershipCriteria criteria);

    Flowable<Membership> findByCriteria(ReferenceType referenceType, MembershipCriteria criteria);

    Maybe<Membership> findByReferenceAndMember(ReferenceType referenceType, String referenceId, MemberType memberType, String memberId);
}
