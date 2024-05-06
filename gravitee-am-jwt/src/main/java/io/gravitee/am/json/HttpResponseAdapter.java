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
package io.gravitee.am.json;

import com.nimbusds.jose.shaded.gson.TypeAdapter;
import com.nimbusds.jose.shaded.gson.stream.JsonReader;
import com.nimbusds.jose.shaded.gson.stream.JsonWriter;
import io.vertx.core.http.impl.Http1xServerResponse;

import java.io.IOException;

public class HttpResponseAdapter extends TypeAdapter<Http1xServerResponse> {
    @Override
    public void write(JsonWriter jsonWriter, Http1xServerResponse http1xServerResponse) throws IOException {
        jsonWriter.nullValue();
    }

    @Override
    public Http1xServerResponse read(JsonReader jsonReader) throws IOException {
        return null;
    }
}
