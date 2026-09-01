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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.oidc.CrossAppAccessResourceServer;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * MongoDB representation of a Cross App Access resource server.
 */
@Getter
@Setter
public class CrossAppAccessResourceServerMongo {

    private String id;
    private String name;
    private String resource;

    public CrossAppAccessResourceServer convert() {
        CrossAppAccessResourceServer resourceServer = new CrossAppAccessResourceServer();
        resourceServer.setId(getId());
        resourceServer.setName(getName());
        resourceServer.setResource(getResource());
        return resourceServer;
    }

    public static CrossAppAccessResourceServerMongo convert(CrossAppAccessResourceServer resourceServer) {
        if (resourceServer == null) {
            return null;
        }
        CrossAppAccessResourceServerMongo mongo = new CrossAppAccessResourceServerMongo();
        mongo.setId(resourceServer.getId());
        mongo.setName(resourceServer.getName());
        mongo.setResource(resourceServer.getResource());
        return mongo;
    }

    public static List<CrossAppAccessResourceServer> toModelList(List<CrossAppAccessResourceServerMongo> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(CrossAppAccessResourceServerMongo::convert).toList();
    }

    public static List<CrossAppAccessResourceServerMongo> fromModelList(List<CrossAppAccessResourceServer> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(CrossAppAccessResourceServerMongo::convert).toList();
    }
}
