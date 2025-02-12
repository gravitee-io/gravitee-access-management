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

import * as runtime from '../runtime';
import {
  ErrorEntity,
  ErrorEntityFromJSON,
  ErrorEntityToJSON,
  NotifierPlugin,
  NotifierPluginFromJSON,
  NotifierPluginToJSON,
} from '../models';

export interface Get31Request {
  notifierId: string;
}

export interface GetSchema6Request {
  notifierId: string;
}

export interface List30Request {
  expand?: Array<string>;
}

/**
 *
 */
export class NotifierApi extends runtime.BaseAPI {
  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a notifier
   */
  async get31Raw(
    requestParameters: Get31Request,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<NotifierPlugin>> {
    if (requestParameters.notifierId === null || requestParameters.notifierId === undefined) {
      throw new runtime.RequiredError(
        'notifierId',
        'Required parameter requestParameters.notifierId was null or undefined when calling get31.',
      );
    }

    const queryParameters: any = {};

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('gravitee-auth', []);

      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }
    const response = await this.request(
      {
        path: `/platform/plugins/notifiers/{notifierId}`.replace(
          `{${'notifierId'}}`,
          encodeURIComponent(String(requestParameters.notifierId)),
        ),
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => NotifierPluginFromJSON(jsonValue));
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a notifier
   */
  async get31(requestParameters: Get31Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<NotifierPlugin> {
    const response = await this.get31Raw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a notifier plugin\'s schema
   */
  async getSchema6Raw(
    requestParameters: GetSchema6Request,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<string>> {
    if (requestParameters.notifierId === null || requestParameters.notifierId === undefined) {
      throw new runtime.RequiredError(
        'notifierId',
        'Required parameter requestParameters.notifierId was null or undefined when calling getSchema6.',
      );
    }

    const queryParameters: any = {};

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('gravitee-auth', []);

      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }
    const response = await this.request(
      {
        path: `/platform/plugins/notifiers/{notifierId}/schema`.replace(
          `{${'notifierId'}}`,
          encodeURIComponent(String(requestParameters.notifierId)),
        ),
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.TextApiResponse(response) as any;
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a notifier plugin\'s schema
   */
  async getSchema6(requestParameters: GetSchema6Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<string> {
    const response = await this.getSchema6Raw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * List all available notifier plugins
   */
  async list30Raw(
    requestParameters: List30Request,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<Array<NotifierPlugin>>> {
    const queryParameters: any = {};

    if (requestParameters.expand) {
      queryParameters['expand'] = requestParameters.expand;
    }

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('gravitee-auth', []);

      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }
    const response = await this.request(
      {
        path: `/platform/plugins/notifiers`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(NotifierPluginFromJSON));
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * List all available notifier plugins
   */
  async list30(
    requestParameters: List30Request = {},
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<Array<NotifierPlugin>> {
    const response = await this.list30Raw(requestParameters, initOverrides);
    return await response.value();
  }
}
