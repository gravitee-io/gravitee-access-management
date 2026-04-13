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
package io.gravitee.am.gateway.handler.aauth.signing;

import java.util.List;

/**
 * Parsed Signature-Input header information.
 * <p>
 * Example: {@code sig=("@method" "@authority" "@path" "signature-key");created=1712345678}
 *
 * @param label             the signature label (e.g. "sig")
 * @param coveredComponents the list of covered component identifiers
 * @param created           the signature creation timestamp (Unix epoch seconds)
 * @param rawValue          the original header value after {@code label=} (used for @signature-params)
 */
public record SignatureInputInfo(
        String label,
        List<String> coveredComponents,
        long created,
        String rawValue
) {
}
