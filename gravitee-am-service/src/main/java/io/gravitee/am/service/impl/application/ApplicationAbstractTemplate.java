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
package io.gravitee.am.service.impl.application;

import static io.gravitee.am.common.oauth2.ResponseType.CODE;
import static io.gravitee.am.common.oauth2.ResponseType.TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.*;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class ApplicationAbstractTemplate implements ApplicationTemplate {

    protected boolean haveAuthorizationCodeResponseTypes(List<String> responseTypes) {
        if (responseTypes == null || responseTypes.isEmpty()) {
            return false;
        }

        return (
            responseTypes.contains(CODE) ||
            responseTypes.contains(CODE_TOKEN) ||
            responseTypes.contains(CODE_ID_TOKEN) ||
            responseTypes.contains(CODE_ID_TOKEN_TOKEN)
        );
    }

    protected boolean haveImplicitResponseTypes(List<String> responseTypes) {
        if (responseTypes == null || responseTypes.isEmpty()) {
            return false;
        }

        return responseTypes.contains(TOKEN) || responseTypes.contains(ID_TOKEN) || responseTypes.contains(ID_TOKEN_TOKEN);
    }

    protected Set<String> defaultAuthorizationCodeResponseTypes() {
        return new HashSet<>(Arrays.asList(CODE, CODE_TOKEN, CODE_ID_TOKEN, CODE_ID_TOKEN_TOKEN));
    }

    protected Set<String> defaultImplicitResponseTypes() {
        return new HashSet<>(Arrays.asList(TOKEN, ID_TOKEN, ID_TOKEN_TOKEN));
    }
}
