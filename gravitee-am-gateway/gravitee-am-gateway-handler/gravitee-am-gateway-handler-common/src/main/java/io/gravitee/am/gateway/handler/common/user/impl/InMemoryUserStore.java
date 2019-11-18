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
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InMemoryUserStore implements UserStore, Handler<Long> {

    /**
     * Default of how often, in ms, to check for expired user sessions
     */
    private static final long DEFAULT_REAPER_INTERVAL = 1000;
    private ConcurrentMap<String, io.gravitee.am.gateway.handler.common.user.impl.User> users = new ConcurrentHashMap<>();
    private long reaperInterval;
    private long timerID = -1;
    private boolean closed;
    private long timeout;
    private Vertx vertx;

    public InMemoryUserStore(Vertx vertx, long timeout) {
        reaperInterval = DEFAULT_REAPER_INTERVAL;
        this.vertx = vertx;
        this.timeout = timeout;
        setTimer();
    }

    @Override
    public void add(User user) {
        users.put(user.getId(), new io.gravitee.am.gateway.handler.common.user.impl.User(user, System.currentTimeMillis()));
    }

    @Override
    public void remove(String userId) {
        users.remove(userId);
    }

    @Override
    public User get(String userId) {
        return users.get(userId) != null ? users.get(userId).getUser() : null;
    }

    @Override
    public void clear() {
        users.clear();
        if (timerID != -1) {
            vertx.cancelTimer(timerID);
        }
        closed = true;
    }

    @Override
    public void handle(Long event) {
        long now = System.currentTimeMillis();
        Set<String> toRemove = new HashSet<>();
        for (io.gravitee.am.gateway.handler.common.user.impl.User user: users.values()) {
            if (now - user.getLastAccessed() > timeout) {
                toRemove.add(user.getUser().getId());
            }
        }
        for (String id: toRemove) {
            users.remove(id);
        }
        if (!closed) {
            setTimer();
        }
    }

    private void setTimer() {
        if (reaperInterval != 0) {
            timerID = vertx.setTimer(reaperInterval, this);
        }
    }
}
