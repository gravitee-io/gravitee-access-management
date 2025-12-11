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
package io.gravitee.am.monitoring;


import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author GraviteeSource Team
 */
@Getter
public class DomainState {
    private final AtomicLong lastSync = new AtomicLong();
    private final Map<String, Boolean> syncState = new ConcurrentHashMap<>();
    private final Map<String, PluginStatus> creationState = new ConcurrentHashMap<>();

    public enum Status {
        INITIALIZING,
        DEPLOYED,
        FAILURE
    }

    private volatile Status status = Status.INITIALIZING;

    public DomainState setStatus(Status status) {
        this.status = status;
        return this;
    }

    public DomainState setLastSync(long lastSync) {
        this.lastSync.set(lastSync);
        return this;
    }

    public void initPluginSync(String pluginId) {
        syncState.put(pluginId, false);
    }

    public void updatePluginState(String pluginId, String pluginName, boolean success, String message) {
        syncState.put(pluginId, true);
        creationState.compute(pluginId, (k, v) -> {
            if (v == null) {
                v = new PluginStatus();
                v.setName(pluginName != null ? pluginName : pluginId);
            } else if (pluginName != null) {
                v.setName(pluginName);
            }
            v.setSuccess(success);
            v.setMessage(message);
            v.setLastSync(System.currentTimeMillis());
            return v;
        });
        this.lastSync.set(System.currentTimeMillis());
    }

    public boolean isStable() {
        return creationState.values().stream().allMatch(PluginStatus::isSuccess);
    }

    public boolean isSynchronized() {
        return syncState.values().stream().allMatch(Boolean::booleanValue);
    }

    @Getter
    @Setter
    public static class PluginStatus {
        private String name;
        private boolean success;
        private String message;
        private long lastSync;
    }
}
