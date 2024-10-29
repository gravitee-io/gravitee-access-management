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
/**
 * <p>
 * Schema classes that enable OpenApi definition to be generated with correct models.
 * Classes in this package should usually only be used as values for the {@link io.swagger.v3.oas.annotations.media.Schema @Schema} annotation.
 * </p>
 * In most cases the {@code @Schema} annotation should use the actual input/output model classes. Dedicated schem classes
 * should only be used when it's not possible, e.g. when the model is generic (as in the case of {@link io.gravitee.am.management.handlers.management.api.bulk.BulkRequest BulkRequest}.
 */
package io.gravitee.am.management.handlers.management.api.schemas;
