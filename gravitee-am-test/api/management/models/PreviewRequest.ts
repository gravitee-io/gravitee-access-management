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
/* Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 *
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

/* tslint:disable */
/* eslint-disable */
import { exists, mapValues } from '../runtime';
import { Theme, ThemeFromJSON, ThemeFromJSONTyped, ThemeToJSON } from './Theme';

/**
 *
 * @export
 * @interface PreviewRequest
 */
export interface PreviewRequest {
  /**
   *
   * @type {string}
   * @memberof PreviewRequest
   */
  content?: string;
  /**
   *
   * @type {Theme}
   * @memberof PreviewRequest
   */
  theme?: Theme;
  /**
   *
   * @type {string}
   * @memberof PreviewRequest
   */
  type: PreviewRequestTypeEnum;
  /**
   *
   * @type {string}
   * @memberof PreviewRequest
   */
  template: string;
}

/**
 * @export
 */
export const PreviewRequestTypeEnum = {
  Email: 'EMAIL',
  Form: 'FORM',
} as const;
export type PreviewRequestTypeEnum = typeof PreviewRequestTypeEnum[keyof typeof PreviewRequestTypeEnum];

export function PreviewRequestFromJSON(json: any): PreviewRequest {
  return PreviewRequestFromJSONTyped(json, false);
}

export function PreviewRequestFromJSONTyped(json: any, ignoreDiscriminator: boolean): PreviewRequest {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    content: !exists(json, 'content') ? undefined : json['content'],
    theme: !exists(json, 'theme') ? undefined : ThemeFromJSON(json['theme']),
    type: json['type'],
    template: json['template'],
  };
}

export function PreviewRequestToJSON(value?: PreviewRequest | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    content: value.content,
    theme: ThemeToJSON(value.theme),
    type: value.type,
    template: value.template,
  };
}
