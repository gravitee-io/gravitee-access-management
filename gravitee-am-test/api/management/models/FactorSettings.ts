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
import { PathFactor, PathApplicationFactorSettingsToJSON } from './PatchApplicationFactor';
import { ApplicationFactorSettingsToJSON } from './ApplicationFactor';

export interface FactorSettings {
  defaultFactorId?: string;
  applicationFactors?: PathFactor[];
}

export function FactorSettingsFromJSON(json: any): FactorSettings {
  return FactorSettingsFromJSONTyped(json, false);
}

export function FactorSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): FactorSettings {
  if (json == null) {
    return json;
  }
  return {
    defaultFactorId: !exists(json, 'defaultFactorId') ? undefined : json['defaultFactorId'],
    applicationFactors: !exists(json, 'factors') ? undefined : ApplicationFactorSettingsToJSON(json['applicationFactors']),
  };
}
