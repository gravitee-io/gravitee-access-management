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

import * as runtime from '../../management/runtime';

export interface GetDomainStateRequest {
  domainId: string;
}

export interface PluginStatus {
  id: string;
  type: string;
  success: boolean;
  message: string;
  lastSync: number;
}

export interface DomainState {
  lastSync: number;
  syncState: Record<string, boolean>;
  creationState: Record<string, PluginStatus>;
  status: string;
  stable: boolean;
  synchronized: boolean;
}

/**
 * Gateway node monitoring API for domain readiness.
 * Maps to the /_node/domains endpoint exposed by DomainReadinessEndpoint.
 */
export class MonitoringApi extends runtime.BaseAPI {
  /**
   * Get the readiness state of a specific domain.
   * Returns 200 if domain is stable and synchronized, 503 if not, 404 if unknown.
   */
  async getDomainStateRaw(
    requestParameters: GetDomainStateRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<DomainState>> {
    if (requestParameters.domainId === null || requestParameters.domainId === undefined) {
      throw new runtime.RequiredError(
        'domainId',
        'Required parameter requestParameters.domainId was null or undefined when calling getDomainState.',
      );
    }

    const queryParameters: any = {};

    queryParameters['domainId'] = requestParameters.domainId;
    queryParameters['output'] = 'json';

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.username !== undefined && this.configuration.password !== undefined) {
      headerParameters['Authorization'] = 'Basic ' + btoa(this.configuration.username + ':' + this.configuration.password);
    }
    const response = await this.request(
      {
        path: `/domains`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response);
  }

  /**
   * Get the readiness state of a specific domain.
   * Returns 200 if domain is stable and synchronized, 503 if not, 404 if unknown.
   */
  async getDomainState(
    requestParameters: GetDomainStateRequest,
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<DomainState> {
    const response = await this.getDomainStateRaw(requestParameters, initOverrides);
    return await response.value();
  }

  /**
   * Get the readiness state of all domains.
   * Returns 200 if all domains are ready, 503 if any are not.
   */
  async getAllDomainStatesRaw(
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<runtime.ApiResponse<Record<string, DomainState>>> {
    const queryParameters: any = {};

    queryParameters['output'] = 'json';

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.username !== undefined && this.configuration.password !== undefined) {
      headerParameters['Authorization'] = 'Basic ' + btoa(this.configuration.username + ':' + this.configuration.password);
    }
    const response = await this.request(
      {
        path: `/domains`,
        method: 'GET',
        headers: headerParameters,
        query: queryParameters,
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response);
  }

  /**
   * Get the readiness state of all domains.
   * Returns 200 if all domains are ready, 503 if any are not.
   */
  async getAllDomainStates(
    initOverrides?: RequestInit | runtime.InitOverideFunction,
  ): Promise<Record<string, DomainState>> {
    const response = await this.getAllDomainStatesRaw(initOverrides);
    return await response.value();
  }
}
