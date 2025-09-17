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
package io.gravitee.am.service.model;

import dev.openfga.sdk.api.model.Store;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OpenFGAStoreEntity {


    private String id;
    private String name;
    private Date createdAt;
    private Date updatedAt;

    public static OpenFGAStoreEntity fromStore(Store store) {
        OpenFGAStoreEntity entity = new OpenFGAStoreEntity();
        entity.setId(store.getId());
        entity.setName(store.getName());
        entity.setCreatedAt(Date.from(store.getCreatedAt().toInstant()));
        entity.setUpdatedAt(Date.from(store.getUpdatedAt().toInstant()));
        return entity;
    }
}