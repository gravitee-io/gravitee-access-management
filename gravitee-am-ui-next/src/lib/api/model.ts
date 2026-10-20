/*
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
 * A model generated from `docs/mapi/openapi.yaml`, imported from `@am-models/*`. The spec marks almost no response
 * field as required, so `K` names the fields the Management API always sends.
 */
export type ApiModel<T, K extends keyof T = never> = Readonly<T & Required<Pick<T, K>>>;
