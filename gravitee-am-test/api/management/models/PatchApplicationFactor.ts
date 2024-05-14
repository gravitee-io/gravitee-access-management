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
/* tslint:disable */
/* eslint-disable */

import { exists } from '../runtime';

export interface PathFactor {
  id?: string;
  selectionRule?: string;
}

export function PathFactorFromJSON(json: any): PathFactor {
  return PathApplicationFactorFromJSONTyped(json, false);
}

export function PathApplicationFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): PathFactor {
  if (json == null) {
    return json;
  }
  return {
    id: !exists(json, 'id') ? undefined : json['id'],
    selectionRule: !exists(json, 'selectionRule') ? undefined : json['selectionRule'],
  };
}

export function PathApplicationFactorSettingsToJSON(value?: PathFactor | null): any {
  if (value == null) {
    return value;
  }
  return {
    id: value.id,
    selectionRule: value.selectionRule,
  };
}
