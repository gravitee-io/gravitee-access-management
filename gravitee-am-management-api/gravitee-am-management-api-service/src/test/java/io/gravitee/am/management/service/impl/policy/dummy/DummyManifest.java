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
package io.gravitee.am.management.service.impl.policy.dummy;

import io.gravitee.plugin.core.api.PluginDependency;
import io.gravitee.plugin.core.api.PluginManifest;

import java.util.List;
import java.util.Map;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyManifest implements PluginManifest {

    private final String id;

    public DummyManifest(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public String category() {
        return null;
    }

    @Override
    public String version() {
        return null;
    }

    @Override
    public String plugin() {
        return null;
    }

    @Override
    public String type() {
        return null;
    }

    @Override
    public String feature() {
        return null;
    }

    @Override
    public int priority() {
        return PluginManifest.super.priority();
    }

    @Override
    public List<PluginDependency> dependencies() {
        return PluginManifest.super.dependencies();
    }

    @Override
    public Map<String, String> properties() {
        return PluginManifest.super.properties();
    }
}
