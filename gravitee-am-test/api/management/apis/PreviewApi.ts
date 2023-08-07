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
/**
 * Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 *
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import * as runtime from '../runtime';
import {
  PreviewRequest,
  PreviewRequestFromJSON,
  PreviewRequestToJSON,
  PreviewResponse,
  PreviewResponseFromJSON,
  PreviewResponseToJSON,
} from '../models';

export interface RenderDomainTemplateRequest {
  organizationId: string;
  environmentId: string;
  domain: string;
  body?: PreviewRequest;
}

/**
 *
 */
export class PreviewApi extends runtime.BaseAPI {
  /**
   * User must have the DOMAIN_THEME[READ] permission on the specified domain or DOMAIN_THEME[READ] permission on the specified environment or DOMAIN_THEME[READ] permission on the specified organization
   * Render the provided template
   */
  async renderDomainTemplateRaw(
    requestParameters: RenderDomainTemplateRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<PreviewResponse>> {
    if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
      throw new runtime.RequiredError(
        'organizationId',
        'Required parameter requestParameters.organizationId was null or undefined when calling renderDomainTemplate.',
      );
    }

    if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
      throw new runtime.RequiredError(
        'environmentId',
        'Required parameter requestParameters.environmentId was null or undefined when calling renderDomainTemplate.',
      );
    }

    if (requestParameters.domain === null || requestParameters.domain === undefined) {
      throw new runtime.RequiredError(
        'domain',
        'Required parameter requestParameters.domain was null or undefined when calling renderDomainTemplate.',
      );
    }

    const queryParameters: any = {};

    const headerParameters: runtime.HTTPHeaders = {};

    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.apiKey) {
      headerParameters['Authorization'] = this.configuration.apiKey('Authorization'); // gravitee-auth authentication
    }

    const response = await this.request(
      {
        path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/forms/preview`
          .replace(`{${'organizationId'}}`, encodeURIComponent(String(requestParameters.organizationId)))
          .replace(`{${'environmentId'}}`, encodeURIComponent(String(requestParameters.environmentId)))
          .replace(`{${'domain'}}`, encodeURIComponent(String(requestParameters.domain))),
        method: 'POST',
        headers: headerParameters,
        query: queryParameters,
        body: PreviewRequestToJSON(requestParameters.body),
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => PreviewResponseFromJSON(jsonValue));
  }

  /**
   * User must have the DOMAIN_THEME[READ] permission on the specified domain or DOMAIN_THEME[READ] permission on the specified environment or DOMAIN_THEME[READ] permission on the specified organization
   * Render the provided template
   */
  async renderDomainTemplate(
    requestParameters: RenderDomainTemplateRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<PreviewResponse> {
    const response = await this.renderDomainTemplateRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
