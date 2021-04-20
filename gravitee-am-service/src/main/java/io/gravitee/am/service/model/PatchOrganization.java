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

import io.gravitee.am.model.Organization;
import io.gravitee.am.service.utils.SetterUtils;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchOrganization {

    public PatchOrganization() {}

    private Optional<List<String>> identities = Optional.empty();

    public Organization patch(Organization _toPatch) {
        // create new object for audit purpose (patch json result)
        Organization toPatch = new Organization(_toPatch);

        SetterUtils.safeSet(toPatch::setIdentities, this.identities);

        return toPatch;
    }

    public void setIdentities(List<String> identities) {
        this.identities = Optional.ofNullable(identities);
    }
}
