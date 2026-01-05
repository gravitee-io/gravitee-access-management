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

import com.mongodb.client.model.Updates;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.OrganizationUserMongo;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoOrganizationUserRepository extends AbstractUserRepository<OrganizationUserMongo> implements OrganizationUserRepository {

    public static final String IDP_GRAVITEE = "gravitee";

    @PostConstruct
    public void init() {
        super.initCollection("organization_users");
    }

    @Override
    protected Class getMongoClass() {
        return OrganizationUserMongo.class;
    }

    @Override
    protected User convert(OrganizationUserMongo userMongo) {
        User user = super.convert(userMongo);
        // organization user may have password if it belongs to the gravitee idp.
        if (IDP_GRAVITEE.equals(userMongo.getSource()) && (userMongo.getServiceAccount() == null || userMongo.getServiceAccount().equals(Boolean.FALSE))) {
            user.setPassword(userMongo.getPassword());
        }
        return user;
    }

    @Override
    protected OrganizationUserMongo convert(User user) {
        if (user == null) {
            return null;
        }
        final OrganizationUserMongo userMongo = new OrganizationUserMongo();
        // organization user may have to store password if it belongs to the gravitee idp.
        if (IDP_GRAVITEE.equals(user.getSource())) {
            userMongo.setPassword(user.getPassword());
        }
        return convert(user, userMongo);
    }

    @Override
    protected ArrayList<Bson> generateUserUpdates(User item, UpdateActions actions) {
        var updates = super.generateUserUpdates(item, actions);
        if (IDP_GRAVITEE.equals(item.getSource()) && (item.getServiceAccount() == null || item.getServiceAccount().equals(Boolean.FALSE))) {
            updates.add(Updates.set("password", item.getPassword()));
        }
        return updates;
    }

    @Override
    protected boolean acceptUpsert() {
        return false;
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        if (!IDP_GRAVITEE.equals(source)) {
            return super.findByUsernameAndSource(referenceType, referenceId, username, source);
        }

        // Apply case-insensitive matching to the username field
        String escapedUsername = Pattern.quote(username);
        Pattern pattern = Pattern.compile("^" + escapedUsername + "$", Pattern.CASE_INSENSITIVE);
        return Observable.fromPublisher(withMaxTime(
                        usersCollection
                                .find(and(
                                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                        eq(FIELD_REFERENCE_ID, referenceId),
                                        regex(FIELD_USERNAME, pattern),
                                        eq(FIELD_SOURCE, source))))
                                .limit(1)
                                .first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }
}
