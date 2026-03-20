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

package io.gravitee.am.service.http;

import io.vertx.core.http.PoolOptions;

public class PoolOptionsBuilder {
    public static PoolOptions build(int maxHttpSize) {
        return build(maxHttpSize, maxHttpSize);
    }

    public static PoolOptions build(int maxHttp1Size, int maxHttp2Size) {
        var opt = new PoolOptions();
        opt.setHttp1MaxSize(maxHttp1Size);
        opt.setHttp2MaxSize(maxHttp2Size);
        return opt;
    }
}
