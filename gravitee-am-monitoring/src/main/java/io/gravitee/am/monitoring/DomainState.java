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

import io.gravitee.common.component.Lifecycle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author GraviteeSource Team
 */
public class DomainState {
    private long lastSync;
    private final Map<String, PluginState> plugins = new ConcurrentHashMap<>();

    public boolean isSynchronized() {
        return plugins.values().stream().allMatch(p -> p.getState() != Lifecycle.State.STARTED && p.getState() != Lifecycle.State.INITIALIZED);
    }

    public boolean isStable() {
        return plugins.values().stream().allMatch(p -> p.getState() != Lifecycle.State.STOPPED);
    }

    public Map<String, PluginState> getPlugins() {
        return plugins;
    }

    public void updatePluginState(String pluginId, String pluginName, Lifecycle.State state) {
        plugins.compute(pluginId, (k, v) -> {
            if (v == null) {
                v = new PluginState();
                v.setName(pluginName != null ? pluginName : pluginId);
            } else if (pluginName != null) {
                v.setName(pluginName);
            }
            v.setState(state);
            v.setLastSync(System.currentTimeMillis());
            return v;
        });
        this.lastSync = System.currentTimeMillis();
    }

    public static class PluginState {
        private String name;
        private Lifecycle.State state;
        private long lastSync;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Lifecycle.State getState() {
            return state;
        }

        public void setState(Lifecycle.State state) {
            this.state = state;
        }

        public long getLastSync() {
            return lastSync;
        }

        public void setLastSync(long lastSync) {
            this.lastSync = lastSync;
        }
    }
}
