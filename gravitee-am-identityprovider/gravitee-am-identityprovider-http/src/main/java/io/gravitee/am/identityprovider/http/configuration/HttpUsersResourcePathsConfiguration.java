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
package io.gravitee.am.identityprovider.http.configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HttpUsersResourcePathsConfiguration {

    private HttpResourceConfiguration createResource;
    private HttpResourceConfiguration readResource;
    private HttpResourceConfiguration updateResource;
    private HttpResourceConfiguration deleteResource;

    public HttpResourceConfiguration getCreateResource() {
        return createResource;
    }

    public void setCreateResource(HttpResourceConfiguration createResource) {
        this.createResource = createResource;
    }

    public HttpResourceConfiguration getReadResource() {
        return readResource;
    }

    public void setReadResource(HttpResourceConfiguration readResource) {
        this.readResource = readResource;
    }

    public HttpResourceConfiguration getUpdateResource() {
        return updateResource;
    }

    public void setUpdateResource(HttpResourceConfiguration updateResource) {
        this.updateResource = updateResource;
    }

    public HttpResourceConfiguration getDeleteResource() {
        return deleteResource;
    }

    public void setDeleteResource(HttpResourceConfiguration deleteResource) {
        this.deleteResource = deleteResource;
    }
}
