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
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.UserMongo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUserRepository extends AbstractManagementMongoRepository implements UserRepository {

    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_USERNAME = "username";

    @PostConstruct
    public void ensureIndexes() {
        mongoOperations.indexOps(UserMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC));

        mongoOperations.indexOps(UserMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC)
                        .on(FIELD_USERNAME, Sort.Direction.ASC));
    }

    @Override
    public Set<User> findByDomain(String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));

        return mongoOperations
                .find(query, UserMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Page<User> findByDomain(String domain, int page, int size) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));
        query.with(new PageRequest(page, size));

        long totalCount = mongoOperations.count(query, UserMongo.class);

        Set<User> users = mongoOperations
                .find(query, UserMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());

        return new Page(users, page, totalCount);
    }

    @Override
    public Optional<User> findByUsernameAndDomain(String username, String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain).and(FIELD_USERNAME).is(username));

        return Optional.ofNullable(convert(mongoOperations.findOne(query, UserMongo.class)));
    }

    @Override
    public Optional<User> findById(String userId) throws TechnicalException {
        return Optional.ofNullable(convert(mongoOperations.findById(userId, UserMongo.class)));
    }

    @Override
    public User create(User item) throws TechnicalException {
        UserMongo user = convert(item);
        mongoOperations.save(user);
        return convert(user);
    }

    @Override
    public User update(User item) throws TechnicalException {
        UserMongo user = convert(item);
        mongoOperations.save(user);
        return convert(user);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        UserMongo user = mongoOperations.findById(id, UserMongo.class);
        mongoOperations.remove(user);

    }

    private User convert(UserMongo userMongo) {
        if (userMongo == null) {
            return null;
        }

        User user = new User();
        user.setId(userMongo.getId());
        user.setUsername(userMongo.getUsername());
        user.setPassword(userMongo.getPassword());
        user.setEmail(userMongo.getEmail());
        user.setFirstName(userMongo.getFirstName());
        user.setLastName(userMongo.getLastName());
        user.setAccountNonExpired(userMongo.isAccountNonExpired());
        user.setAccountNonLocked(userMongo.isAccountNonLocked());
        user.setCredentialsNonExpired(userMongo.isCredentialsNonExpired());
        user.setEnabled(userMongo.isEnabled());
        user.setDomain(userMongo.getDomain());
        user.setSource(userMongo.getSource());
        user.setClient(userMongo.getClient());
        user.setLoginsCount(userMongo.getLoginsCount());
        user.setLoggedAt(userMongo.getLoggedAt());
        user.setAdditionalInformation(userMongo.getAdditionalInformation());
        user.setCreatedAt(userMongo.getCreatedAt());
        user.setUpdatedAt(userMongo.getUpdatedAt());
        return user;
    }

    private UserMongo convert(User user) {
        if (user == null) {
            return null;
        }

        UserMongo userMongo = new UserMongo();
        userMongo.setId(user.getId());
        userMongo.setUsername(user.getUsername());
        userMongo.setPassword(user.getPassword());
        userMongo.setEmail(user.getEmail());
        userMongo.setFirstName(user.getFirstName());
        userMongo.setLastName(user.getLastName());
        userMongo.setAccountNonExpired(user.isAccountNonExpired());
        userMongo.setAccountNonLocked(user.isAccountNonLocked());
        userMongo.setCredentialsNonExpired(user.isCredentialsNonExpired());
        userMongo.setEnabled(user.isEnabled());
        userMongo.setDomain(user.getDomain());
        userMongo.setSource(user.getSource());
        userMongo.setClient(user.getClient());
        userMongo.setLoginsCount(user.getLoginsCount());
        userMongo.setLoggedAt(user.getLoggedAt());
        userMongo.setAdditionalInformation(user.getAdditionalInformation());
        userMongo.setCreatedAt(user.getCreatedAt());
        userMongo.setUpdatedAt(user.getUpdatedAt());
        return userMongo;
    }
}
