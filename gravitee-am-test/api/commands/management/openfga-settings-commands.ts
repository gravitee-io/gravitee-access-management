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

const request = require('supertest');

const openFgaSettingsPath = (domainId: string, engineId: string) =>
  `/management/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/authorization-engines/${engineId}/settings`;

export interface OpenFgaStore {
  id: string;
  name: string;
  created_at?: string;
  updated_at?: string;
}

export interface AuthorizationModel {
  id: string;
  authorizationModelId?: string;
  schema_version?: string;
  type_definitions?: any[];
}

export interface AuthorizationModelsResponse {
  data: AuthorizationModel[];
  info?: {
    continuation_token?: string;
  };
}

export interface Tuple {
  user: string;
  relation: string;
  object: string;
}

export interface TuplesResponse {
  data: Tuple[];
  info?: {
    continuation_token?: string;
  };
}

export interface CheckPermissionRequest {
  user: string;
  relation: string;
  object: string;
}

export interface CheckPermissionResponse {
  allowed: boolean;
  context?: any;
}

export async function getOpenFgaStore(domainId: string, engineId: string, accessToken: string): Promise<OpenFgaStore> {
  const response = await request(process.env.AM_MANAGEMENT_URL)
    .get(`${openFgaSettingsPath(domainId, engineId)}/store`)
    .set('Authorization', `Bearer ${accessToken}`)
    .expect(200);
  return response.body;
}

export async function createAuthorizationModel(
  domainId: string,
  engineId: string,
  accessToken: string,
  model: any,
): Promise<AuthorizationModel> {
  const response = await request(process.env.AM_MANAGEMENT_URL)
    .post(`${openFgaSettingsPath(domainId, engineId)}/authorization-models`)
    .set('Authorization', `Bearer ${accessToken}`)
    .send(model)
    .expect(201);
  return response.body;
}

export async function listAuthorizationModels(
  domainId: string,
  engineId: string,
  accessToken: string,
  pageSize?: number,
  continuationToken?: string,
): Promise<AuthorizationModelsResponse> {
  let path = `${openFgaSettingsPath(domainId, engineId)}/authorization-models`;
  const params: string[] = [];

  if (pageSize !== undefined) {
    params.push(`pageSize=${pageSize}`);
  }
  if (continuationToken) {
    params.push(`continuationToken=${continuationToken}`);
  }

  if (params.length > 0) {
    path += '?' + params.join('&');
  }

  const response = await request(process.env.AM_MANAGEMENT_URL).get(path).set('Authorization', `Bearer ${accessToken}`).expect(200);
  return response.body;
}

export async function addTuple(domainId: string, engineId: string, accessToken: string, tuple: Tuple): Promise<void> {
  await request(process.env.AM_MANAGEMENT_URL)
    .post(`${openFgaSettingsPath(domainId, engineId)}/tuples`)
    .set('Authorization', `Bearer ${accessToken}`)
    .send(tuple)
    .expect(201);
}

export async function listTuples(
  domainId: string,
  engineId: string,
  accessToken: string,
  pageSize?: number,
  continuationToken?: string,
): Promise<TuplesResponse> {
  let path = `${openFgaSettingsPath(domainId, engineId)}/tuples`;
  const params: string[] = [];

  if (pageSize !== undefined) {
    params.push(`pageSize=${pageSize}`);
  }
  if (continuationToken) {
    params.push(`continuationToken=${continuationToken}`);
  }

  if (params.length > 0) {
    path += '?' + params.join('&');
  }

  const response = await request(process.env.AM_MANAGEMENT_URL).get(path).set('Authorization', `Bearer ${accessToken}`).expect(200);
  return response.body;
}

export async function deleteTuple(domainId: string, engineId: string, accessToken: string, tuple: Tuple): Promise<void> {
  await request(process.env.AM_MANAGEMENT_URL)
    .delete(`${openFgaSettingsPath(domainId, engineId)}/tuples`)
    .set('Authorization', `Bearer ${accessToken}`)
    .send(tuple)
    .expect(204);
}

export async function checkPermission(
  domainId: string,
  engineId: string,
  accessToken: string,
  check: CheckPermissionRequest,
): Promise<CheckPermissionResponse> {
  const response = await request(process.env.AM_MANAGEMENT_URL)
    .post(`${openFgaSettingsPath(domainId, engineId)}/check`)
    .set('Authorization', `Bearer ${accessToken}`)
    .send(check)
    .expect(200);
  return response.body;
}
