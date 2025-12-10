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


import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author GraviteeSource Team
 */
public class DomainState {
    private final AtomicLong lastSync = new AtomicLong();
    private final Map<String, PluginState> plugins = new ConcurrentHashMap<>();

    public enum Status {
        INITIALIZING,
        DEPLOYED,
        FAILURE
    }

    private volatile Status status = Status.INITIALIZING;

    public boolean isSynchronized() {
        return plugins.values().stream().allMatch(PluginState::isSuccess);
    }

    public boolean isStable() {
        return plugins.values().stream().allMatch(PluginState::isSuccess);
    }

    public Status getStatus() {
        return status;
    }

    public DomainState setStatus(Status status) {
        this.status = status;
        return this;
    }

    public Map<String, PluginState> getPlugins() {
        return plugins;
    }

    public long getLastSync() {
        return lastSync.get();
    }

    public DomainState setLastSync(long lastSync) {
        this.lastSync.set(lastSync);
        return this;
    }

    public void updatePluginState(String pluginId, String pluginName, boolean success, String message) {
        PluginState updatedPlugin = plugins.compute(pluginId, (k, v) -> {
            if (v == null) {
                v = new PluginState();
                v.setName(pluginName != null ? pluginName : pluginId);
            } else if (pluginName != null) {
                v.setName(pluginName);
            }
            v.setSuccess(success);
            v.setMessage(message);
            v.setLastSync(System.currentTimeMillis());
            return v;
        });
        this.lastSync.set(updatedPlugin.getLastSync());
    }

    public static class PluginState {
        private String name;
        private boolean success;
        private String message;
        private long lastSync;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public long getLastSync() {
            return lastSync;
        }

        public void setLastSync(long lastSync) {
            this.lastSync = lastSync;
        }
    }
}
