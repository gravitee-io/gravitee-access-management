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
package io.gravitee.am.common.analytics;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Field {
    String USER_STATUS = "user_status";
    String USER_REGISTRATION = "user_registration";
    String APPLICATION = "application";
    String USER = "user";
    String USER_LOGIN = "user_login";
    String WEBAUTHN = "webauthn";
    String CBA = "cba";


    static Collection<String> types() {
        return Arrays.asList(USER_STATUS, USER_REGISTRATION, APPLICATION, USER, USER_LOGIN, WEBAUTHN, CBA);
    }
}
