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
package io.gravitee.am.gateway.handler.root.resources.handler.user.register;

import io.gravitee.am.gateway.handler.root.resources.handler.user.UserBodyRequestParseHandler;

import java.util.Arrays;
import java.util.List;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.EMAIL_PARAM_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.PASSWORD_PARAM_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterSubmissionRequestParseHandler extends UserBodyRequestParseHandler {

    public static final List<String> requiredParams = Arrays.asList("username", EMAIL_PARAM_KEY, PASSWORD_PARAM_KEY, "firstname", "lastname");

    public RegisterSubmissionRequestParseHandler() {
        super(requiredParams);
    }
}
