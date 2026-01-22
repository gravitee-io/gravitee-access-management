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
package io.gravitee.am.identityprovider.api.social;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.stream.ReadStream;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProviderResponseModeTest {

    @Test
    public void extract_parameters_query() {
        Request request = mock(Request.class);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", "abc");
        params.add("state", "xyz");
        when(request.parameters()).thenReturn(params);

        MultiValueMap<String, String> result = ProviderResponseMode.QUERY.extractParameters(request);

        assertEquals("abc", result.getFirst("code"));
        assertEquals("xyz", result.getFirst("state"));
    }

    @Test
    public void extract_parameters_fragment() {
        Request request = mock(Request.class);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ConstantKeys.URL_HASH_PARAMETER, "#code=abc&state=xyz");
        when(request.parameters()).thenReturn(params);

        MultiValueMap<String, String> result = ProviderResponseMode.FRAGMENT.extractParameters(request);

        assertEquals("abc", result.getFirst("code"));
        assertEquals("xyz", result.getFirst("state"));
    }

    @Test
    public void extract_parameters_form_post() {
        Request request = mock(Request.class);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", "abc");
        params.add("state", "xyz");
        when(request.parameters()).thenReturn(params);

        MultiValueMap<String, String> result = ProviderResponseMode.FORM_POST.extractParameters(request);

        assertEquals("abc", result.getFirst("code"));
        assertEquals("xyz", result.getFirst("state"));
    }
}
