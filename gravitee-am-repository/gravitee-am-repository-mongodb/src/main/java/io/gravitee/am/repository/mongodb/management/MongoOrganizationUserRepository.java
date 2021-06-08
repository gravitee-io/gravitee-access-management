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

import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.OrganizationUserMongo;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoOrganizationUserRepository extends AbstractUserRepository<OrganizationUserMongo>  implements OrganizationUserRepository {

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
        if (IDP_GRAVITEE.equals(userMongo.getSource())) {
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
}
