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

package io.gravitee.am.gateway.handler.root.resources.handler.dummies;

import io.vertx.reactivex.ext.web.Session;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummySession extends Session {

    private final Map<String, Object> data = new HashMap<>();

    public DummySession() {
        super(null);
    }

    @Override
    public io.vertx.ext.web.Session getDelegate() {
        return new io.vertx.ext.web.Session() {
            @Override
            public io.vertx.ext.web.Session regenerateId() {
                return null;
            }

            @Override
            public String id() {
                return null;
            }

            @Override
            public io.vertx.ext.web.Session put(String s, Object o) {
                return null;
            }

            @Override
            public io.vertx.ext.web.Session putIfAbsent(String s, Object o) {
                return null;
            }

            @Override
            public io.vertx.ext.web.Session computeIfAbsent(String s, Function<String, Object> function) {
                return null;
            }

            @Override
            public <T> T get(String s) {
                return (T) data.get(s);
            }

            @Override
            public <T> T remove(String s) {
                return null;
            }

            @Override
            public Map<String, Object> data() {
                return null;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long lastAccessed() {
                return 0;
            }

            @Override
            public void destroy() {

            }

            @Override
            public boolean isDestroyed() {
                return false;
            }

            @Override
            public boolean isRegenerated() {
                return false;
            }

            @Override
            public String oldId() {
                return null;
            }

            @Override
            public long timeout() {
                return 0;
            }

            @Override
            public void setAccessed() {

            }
        };
    }

    @Override
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    @Override
    public Session put(String key, Object obj) {
        data.put(key, obj);
        return this;
    }
}
