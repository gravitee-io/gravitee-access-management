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
package io.gravitee.am.gateway.handler.common.client.cimd;

import java.util.Arrays;
import java.util.Objects;

/**
 * Cached bytes for a CIMD client logo, together with the content-type and cache TTL.
 */
public record CachedLogo(byte[] bytes, String contentType, long maxAgeSeconds) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CachedLogo that = (CachedLogo) o;
        return maxAgeSeconds == that.maxAgeSeconds
                && Arrays.equals(bytes, that.bytes)
                && Objects.equals(contentType, that.contentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(bytes), contentType, maxAgeSeconds);
    }

    @Override
    public String toString() {
        return "CachedLogo[byteCount=" + (bytes == null ? 0 : bytes.length)
                + ", contentType=" + contentType
                + ", maxAgeSeconds=" + maxAgeSeconds
                + "]";
    }
}
