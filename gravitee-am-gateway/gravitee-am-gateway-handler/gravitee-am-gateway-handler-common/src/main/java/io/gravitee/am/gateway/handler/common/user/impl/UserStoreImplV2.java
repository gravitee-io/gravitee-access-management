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

package io.gravitee.am.gateway.handler.common.user.impl;


import io.gravitee.am.gateway.handler.common.user.UserStore;
import io.gravitee.am.model.User;
import io.gravitee.node.api.cache.Cache;
import io.gravitee.node.api.cache.CacheManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserStoreImplV2 implements UserStore {

    private static final String SEPARATOR = ":";

    private Cache<String, User> idCache;
    private Cache<String, User> gisCache;

    private int ttl;

    public UserStoreImplV2(CacheManager cacheManager, Environment environment) {
        this.idCache = cacheManager.getOrCreateCache("userStoreById");
        this.gisCache = cacheManager.getOrCreateCache("userStoreByGis");
        this.ttl = environment.getProperty("http.cookie.session.cache.ttl", Integer.class, 36000);
    }

    @Override
    public Maybe<User> add(User user) {
        return idCache.rxPut(user.getId(), user, ttl, TimeUnit.SECONDS)
                .concatWith(gisCache.rxPut(generateInternalSubFrom(user.getSource(), user.getExternalId()), user, ttl, TimeUnit.SECONDS))
                .lastElement();
    }

    @Override
    public Completable remove(String userId) {
        return idCache.rxEvict(userId).flatMap(user -> gisCache.rxEvict(generateInternalSubFrom(user.getSource(), user.getExternalId()))).ignoreElement();
    }

    @Override
    public Maybe<User> get(String userId) {
        return idCache.rxGet(userId);
    }

    @Override
    public Completable removeByInternalSub(String gis) {
        return gisCache.rxEvict(gis).flatMap(user -> idCache.rxEvict(user.getId())).ignoreElement();
    }

    @Override
    public Maybe<User> getByInternalSub(String gis) {
        return gisCache.rxGet(gis);
    }

    @Override
    public Completable clear() {
        return gisCache.rxClear().andThen(idCache.rxClear());
    }

    private String generateInternalSubFrom(String src, String externalId) {
        return src + SEPARATOR + externalId;
    }
}
