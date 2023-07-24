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

import io.gravitee.plugin.core.api.PluginManifest;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.policy.api.PolicyContext;
import java.net.URL;
import java.nio.file.Path;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyPolicy implements PolicyPlugin {

    private final boolean deployed;

    private final PluginManifest manifest;

    public DummyPolicy(PluginManifest manifest) {
        this(manifest, true);
    }

    public DummyPolicy(PluginManifest manifest, boolean deployed) {
        this.deployed = deployed;
        this.manifest = manifest;
    }

    @Override
    public Class<?> policy() {
        return null;
    }

    @Override
    public Class<? extends PolicyContext> context() {
        return null;
    }

    @Override
    public String type() {
        return PolicyPlugin.super.type();
    }

    @Override
    public Class<DummyPolicy> configuration() {
        return null;
    }

    @Override
    public String id() {
        return manifest.id();
    }

    @Override
    public String clazz() {
        return null;
    }

    @Override
    public Path path() {
        return null;
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public URL[] dependencies() {
        return new URL[0];
    }

    @Override
    public boolean deployed() {
        return deployed;
    }
}
