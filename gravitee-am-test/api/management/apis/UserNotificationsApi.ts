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
import { UserNotificationContent, UserNotificationContentFromJSON, UserNotificationContentToJSON } from '../models';

export interface MarkAsReadRequest {
  notificationId: string;
}

/**
 *
 */
export class UserNotificationsApi extends runtime.BaseAPI {
  /**
   * List notifications received by the current user
   */
  async listNotificationsRaw(
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<Array<UserNotificationContent>>> {
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
        path: `/user/notifications`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(UserNotificationContentFromJSON));
  }

  /**
   * List notifications received by the current user
   */
  async listNotifications(initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Array<UserNotificationContent>> {
    const response = await this.listNotificationsRaw(initOverrides);
    return await response.value();
  }

  /**
   * Mark User notification as read
   */
  async markAsReadRaw(
    requestParameters: MarkAsReadRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<void>> {
    if (requestParameters.notificationId === null || requestParameters.notificationId === undefined) {
      throw new runtime.RequiredError(
        'notificationId',
        'Required parameter requestParameters.notificationId was null or undefined when calling markAsRead.',
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
        path: `/user/notifications/{notificationId}/acknowledge`.replace(
          `{${'notificationId'}}`,
          encodeURIComponent(String(requestParameters.notificationId)),
        ),
        method: 'POST',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.VoidApiResponse(response);
  }

  /**
   * Mark User notification as read
   */
  async markAsRead(requestParameters: MarkAsReadRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
    await this.markAsReadRaw(requestParameters, initOverrides);
  }
}
