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
  BotDetection,
  BotDetectionFromJSON,
  BotDetectionToJSON,
  NewBotDetection,
  NewBotDetectionFromJSON,
  NewBotDetectionToJSON,
  UpdateBotDetection,
  UpdateBotDetectionFromJSON,
  UpdateBotDetectionToJSON,
} from '../models';

export interface CreateBotDetectionRequest {
  organizationId: string;
  environmentId: string;
  domain: string;
  newBotDetection: NewBotDetection;
}

export interface DeleteBotDetectionRequest {
  organizationId: string;
  environmentId: string;
  domain: string;
  botDetection: string;
}

export interface Get26Request {
  botDetection: string;
}

export interface GetBotDetectionRequest {
  organizationId: string;
  environmentId: string;
  domain: string;
  botDetection: string;
}

export interface GetSchema1Request {
  botDetection: string;
}

export interface ListBotDetectionsRequest {
  organizationId: string;
  environmentId: string;
  domain: string;
}

export interface UpdateBotDetectionRequest {
  organizationId: string;
  environmentId: string;
  domain: string;
  botDetection: string;
  updateBotDetection: UpdateBotDetection;
}

/**
 *
 */
export class BotDetectionApi extends runtime.BaseAPI {
  /**
   * User must have the DOMAIN_BOT_DETECTION[CREATE] permission on the specified domain or DOMAIN_BOT_DETECTION[CREATE] permission on the specified environment or DOMAIN_BOT_DETECTION[CREATE] permission on the specified organization
   * Create a bot detection
   */
  async createBotDetectionRaw(
    requestParameters: CreateBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<BotDetection>> {
    if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
      throw new runtime.RequiredError(
        'organizationId',
        'Required parameter requestParameters.organizationId was null or undefined when calling createBotDetection.',
      );
    }

    if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
      throw new runtime.RequiredError(
        'environmentId',
        'Required parameter requestParameters.environmentId was null or undefined when calling createBotDetection.',
      );
    }

    if (requestParameters.domain === null || requestParameters.domain === undefined) {
      throw new runtime.RequiredError(
        'domain',
        'Required parameter requestParameters.domain was null or undefined when calling createBotDetection.',
      );
    }

    if (requestParameters.newBotDetection === null || requestParameters.newBotDetection === undefined) {
      throw new runtime.RequiredError(
        'newBotDetection',
        'Required parameter requestParameters.newBotDetection was null or undefined when calling createBotDetection.',
      );
    }

    const queryParameters: any = {};

    const headerParameters: runtime.HTTPHeaders = {};

    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('gravitee-auth', []);

      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }
    const response = await this.request(
      {
        path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/bot-detections`
          .replace(`{${'organizationId'}}`, encodeURIComponent(String(requestParameters.organizationId)))
          .replace(`{${'environmentId'}}`, encodeURIComponent(String(requestParameters.environmentId)))
          .replace(`{${'domain'}}`, encodeURIComponent(String(requestParameters.domain))),
        method: 'POST',
        headers: headerParameters,
        query: queryParameters,
        body: NewBotDetectionToJSON(requestParameters.newBotDetection),
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => BotDetectionFromJSON(jsonValue));
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[CREATE] permission on the specified domain or DOMAIN_BOT_DETECTION[CREATE] permission on the specified environment or DOMAIN_BOT_DETECTION[CREATE] permission on the specified organization
   * Create a bot detection
   */
  async createBotDetection(
    requestParameters: CreateBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<BotDetection> {
    const response = await this.createBotDetectionRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[DELETE] permission on the specified domain or DOMAIN_BOT_DETECTION[DELETE] permission on the specified environment or DOMAIN_BOT_DETECTION[DELETE] permission on the specified organization
   * Delete a bot detection
   */
  async deleteBotDetectionRaw(
    requestParameters: DeleteBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<void>> {
    if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
      throw new runtime.RequiredError(
        'organizationId',
        'Required parameter requestParameters.organizationId was null or undefined when calling deleteBotDetection.',
      );
    }

    if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
      throw new runtime.RequiredError(
        'environmentId',
        'Required parameter requestParameters.environmentId was null or undefined when calling deleteBotDetection.',
      );
    }

    if (requestParameters.domain === null || requestParameters.domain === undefined) {
      throw new runtime.RequiredError(
        'domain',
        'Required parameter requestParameters.domain was null or undefined when calling deleteBotDetection.',
      );
    }

    if (requestParameters.botDetection === null || requestParameters.botDetection === undefined) {
      throw new runtime.RequiredError(
        'botDetection',
        'Required parameter requestParameters.botDetection was null or undefined when calling deleteBotDetection.',
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
        path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/bot-detections/{botDetection}`
          .replace(`{${'organizationId'}}`, encodeURIComponent(String(requestParameters.organizationId)))
          .replace(`{${'environmentId'}}`, encodeURIComponent(String(requestParameters.environmentId)))
          .replace(`{${'domain'}}`, encodeURIComponent(String(requestParameters.domain)))
          .replace(`{${'botDetection'}}`, encodeURIComponent(String(requestParameters.botDetection))),
        method: 'DELETE',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.VoidApiResponse(response);
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[DELETE] permission on the specified domain or DOMAIN_BOT_DETECTION[DELETE] permission on the specified environment or DOMAIN_BOT_DETECTION[DELETE] permission on the specified organization
   * Delete a bot detection
   */
  async deleteBotDetection(
    requestParameters: DeleteBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<void> {
    await this.deleteBotDetectionRaw(requestParameters, initOverrides);
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a Bot Detection plugin
   */
  async get26Raw(
    requestParameters: Get26Request,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<void>> {
    if (requestParameters.botDetection === null || requestParameters.botDetection === undefined) {
      throw new runtime.RequiredError(
        'botDetection',
        'Required parameter requestParameters.botDetection was null or undefined when calling get26.',
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
        path: `/platform/plugins/bot-detections/{botDetection}`.replace(
          `{${'botDetection'}}`,
          encodeURIComponent(String(requestParameters.botDetection)),
        ),
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.VoidApiResponse(response);
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a Bot Detection plugin
   */
  async get26(requestParameters: Get26Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
    await this.get26Raw(requestParameters, initOverrides);
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[READ] permission on the specified domain or DOMAIN_BOT_DETECTION[READ] permission on the specified environment or DOMAIN_BOT_DETECTION[READ] permission on the specified organization
   * Get a bot detection
   */
  async getBotDetectionRaw(
    requestParameters: GetBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<BotDetection>> {
    if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
      throw new runtime.RequiredError(
        'organizationId',
        'Required parameter requestParameters.organizationId was null or undefined when calling getBotDetection.',
      );
    }

    if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
      throw new runtime.RequiredError(
        'environmentId',
        'Required parameter requestParameters.environmentId was null or undefined when calling getBotDetection.',
      );
    }

    if (requestParameters.domain === null || requestParameters.domain === undefined) {
      throw new runtime.RequiredError(
        'domain',
        'Required parameter requestParameters.domain was null or undefined when calling getBotDetection.',
      );
    }

    if (requestParameters.botDetection === null || requestParameters.botDetection === undefined) {
      throw new runtime.RequiredError(
        'botDetection',
        'Required parameter requestParameters.botDetection was null or undefined when calling getBotDetection.',
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
        path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/bot-detections/{botDetection}`
          .replace(`{${'organizationId'}}`, encodeURIComponent(String(requestParameters.organizationId)))
          .replace(`{${'environmentId'}}`, encodeURIComponent(String(requestParameters.environmentId)))
          .replace(`{${'domain'}}`, encodeURIComponent(String(requestParameters.domain)))
          .replace(`{${'botDetection'}}`, encodeURIComponent(String(requestParameters.botDetection))),
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => BotDetectionFromJSON(jsonValue));
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[READ] permission on the specified domain or DOMAIN_BOT_DETECTION[READ] permission on the specified environment or DOMAIN_BOT_DETECTION[READ] permission on the specified organization
   * Get a bot detection
   */
  async getBotDetection(
    requestParameters: GetBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<BotDetection> {
    const response = await this.getBotDetectionRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a Bot Detection plugin\'s schema
   */
  async getSchema1Raw(
    requestParameters: GetSchema1Request,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<void>> {
    if (requestParameters.botDetection === null || requestParameters.botDetection === undefined) {
      throw new runtime.RequiredError(
        'botDetection',
        'Required parameter requestParameters.botDetection was null or undefined when calling getSchema1.',
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
        path: `/platform/plugins/bot-detections/{botDetection}/schema`.replace(
          `{${'botDetection'}}`,
          encodeURIComponent(String(requestParameters.botDetection)),
        ),
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.VoidApiResponse(response);
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * Get a Bot Detection plugin\'s schema
   */
  async getSchema1(requestParameters: GetSchema1Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
    await this.getSchema1Raw(requestParameters, initOverrides);
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * List bot detection plugins
   */
  async list25Raw(initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
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
        path: `/platform/plugins/bot-detections`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.VoidApiResponse(response);
  }

  /**
   * There is no particular permission needed. User must be authenticated.
   * List bot detection plugins
   */
  async list25(initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
    await this.list25Raw(initOverrides);
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[LIST] permission on the specified domain or DOMAIN_BOT_DETECTION[LIST] permission on the specified environment or DOMAIN_BOT_DETECTION[LIST] permission on the specified organization Each returned bot detections is filtered and contains only basic information such as id, name.
   * List registered bot detections for a security domain
   */
  async listBotDetectionsRaw(
    requestParameters: ListBotDetectionsRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<Array<BotDetection>>> {
    if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
      throw new runtime.RequiredError(
        'organizationId',
        'Required parameter requestParameters.organizationId was null or undefined when calling listBotDetections.',
      );
    }

    if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
      throw new runtime.RequiredError(
        'environmentId',
        'Required parameter requestParameters.environmentId was null or undefined when calling listBotDetections.',
      );
    }

    if (requestParameters.domain === null || requestParameters.domain === undefined) {
      throw new runtime.RequiredError(
        'domain',
        'Required parameter requestParameters.domain was null or undefined when calling listBotDetections.',
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
        path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/bot-detections`
          .replace(`{${'organizationId'}}`, encodeURIComponent(String(requestParameters.organizationId)))
          .replace(`{${'environmentId'}}`, encodeURIComponent(String(requestParameters.environmentId)))
          .replace(`{${'domain'}}`, encodeURIComponent(String(requestParameters.domain))),
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(BotDetectionFromJSON));
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[LIST] permission on the specified domain or DOMAIN_BOT_DETECTION[LIST] permission on the specified environment or DOMAIN_BOT_DETECTION[LIST] permission on the specified organization Each returned bot detections is filtered and contains only basic information such as id, name.
   * List registered bot detections for a security domain
   */
  async listBotDetections(
    requestParameters: ListBotDetectionsRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<Array<BotDetection>> {
    const response = await this.listBotDetectionsRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[UPDATE] permission on the specified domain or DOMAIN_BOT_DETECTION[UPDATE] permission on the specified environment or DOMAIN_BOT_DETECTION[UPDATE] permission on the specified organization
   * Update a bot detection
   */
  async updateBotDetectionRaw(
    requestParameters: UpdateBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<BotDetection>> {
    if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
      throw new runtime.RequiredError(
        'organizationId',
        'Required parameter requestParameters.organizationId was null or undefined when calling updateBotDetection.',
      );
    }

    if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
      throw new runtime.RequiredError(
        'environmentId',
        'Required parameter requestParameters.environmentId was null or undefined when calling updateBotDetection.',
      );
    }

    if (requestParameters.domain === null || requestParameters.domain === undefined) {
      throw new runtime.RequiredError(
        'domain',
        'Required parameter requestParameters.domain was null or undefined when calling updateBotDetection.',
      );
    }

    if (requestParameters.botDetection === null || requestParameters.botDetection === undefined) {
      throw new runtime.RequiredError(
        'botDetection',
        'Required parameter requestParameters.botDetection was null or undefined when calling updateBotDetection.',
      );
    }

    if (requestParameters.updateBotDetection === null || requestParameters.updateBotDetection === undefined) {
      throw new runtime.RequiredError(
        'updateBotDetection',
        'Required parameter requestParameters.updateBotDetection was null or undefined when calling updateBotDetection.',
      );
    }

    const queryParameters: any = {};

    const headerParameters: runtime.HTTPHeaders = {};

    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('gravitee-auth', []);

      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }
    const response = await this.request(
      {
        path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/bot-detections/{botDetection}`
          .replace(`{${'organizationId'}}`, encodeURIComponent(String(requestParameters.organizationId)))
          .replace(`{${'environmentId'}}`, encodeURIComponent(String(requestParameters.environmentId)))
          .replace(`{${'domain'}}`, encodeURIComponent(String(requestParameters.domain)))
          .replace(`{${'botDetection'}}`, encodeURIComponent(String(requestParameters.botDetection))),
        method: 'PUT',
        headers: headerParameters,
        query: queryParameters,
        body: UpdateBotDetectionToJSON(requestParameters.updateBotDetection),
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => BotDetectionFromJSON(jsonValue));
  }

  /**
   * User must have the DOMAIN_BOT_DETECTION[UPDATE] permission on the specified domain or DOMAIN_BOT_DETECTION[UPDATE] permission on the specified environment or DOMAIN_BOT_DETECTION[UPDATE] permission on the specified organization
   * Update a bot detection
   */
  async updateBotDetection(
    requestParameters: UpdateBotDetectionRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<BotDetection> {
    const response = await this.updateBotDetectionRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
