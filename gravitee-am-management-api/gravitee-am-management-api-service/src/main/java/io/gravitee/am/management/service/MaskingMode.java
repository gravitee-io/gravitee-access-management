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
package io.gravitee.am.management.service;

/**
 * How a plugin configuration's sensitive values are masked.
 *
 * @author GraviteeSource Team
 */
public enum MaskingMode {
    /** Every sensitive field holds the mask, whether or not a value is set. */
    ALWAYS,
    /** Only a set value is masked; an absent or {@code null} sensitive field is omitted and an empty one is kept. */
    PRESENT_ONLY
}
