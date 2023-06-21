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
package io.gravitee.am.common.plugin;

import io.gravitee.plugin.core.api.ConfigurablePlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginManifest;
import java.net.URL;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AmPlugin<CONFIGURATION, PROVIDER> implements ConfigurablePlugin<CONFIGURATION> {

    private Plugin delegate;

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String clazz() {
        return delegate.clazz();
    }

    @Override
    public String type() {
        return delegate.type();
    }

    @Override
    public Path path() {
        return delegate.path();
    }

    @Override
    public PluginManifest manifest() {
        return delegate.manifest();
    }

    @Override
    public URL[] dependencies() {
        return delegate.dependencies();
    }

    @Override
    public boolean deployed() {
        return delegate.deployed();
    }

    public abstract Class<PROVIDER> provider();

    public Plugin getDelegate() {
        return delegate;
    }

    public void setDelegate(Plugin delegate) {
        this.delegate = delegate;
    }
}
