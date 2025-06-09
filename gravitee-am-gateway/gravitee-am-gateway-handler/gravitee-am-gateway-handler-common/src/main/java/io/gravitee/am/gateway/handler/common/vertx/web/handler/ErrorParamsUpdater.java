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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rxjava3.core.MultiMap;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;

public class ErrorParamsUpdater {

    public static String addErrorParams(MultiMap map, String errorParam, String errorDescription) {
        map.set(ERROR_PARAM_KEY, errorParam);
        map.set(ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
        String toHash = errorParam + "$" + errorDescription;
        return HashUtil.generateSHA256(toHash);
    }


}
