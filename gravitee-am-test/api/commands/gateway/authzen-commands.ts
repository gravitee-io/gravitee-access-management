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

export interface AuthZenSubject {
  type: string;
  id: string;
  properties?: Record<string, any>;
}

export interface AuthZenResource {
  type: string;
  id: string;
  properties?: Record<string, any>;
}

export interface AuthZenAction {
  name: string;
  properties?: Record<string, any>;
}

export interface AuthZenAccessEvaluationRequest {
  subject: AuthZenSubject;
  resource: AuthZenResource;
  action: AuthZenAction;
  context?: Record<string, any>;
}

export interface AuthZenAccessEvaluationResponse {
  decision: boolean;
  context?: Record<string, any>;
}

/**
 * Call AuthZen Access Evaluation API
 * @param domainHrid - Domain HRID (path), not the UUID
 * @param accessToken
 * @param evaluationRequest
 */
export async function evaluateAccess(
  domainHrid: string,
  accessToken: string,
  evaluationRequest: AuthZenAccessEvaluationRequest,
): Promise<AuthZenAccessEvaluationResponse> {
  const response = await request(process.env.AM_GATEWAY_URL)
    .post(`/${domainHrid}/access/v1/evaluation`)
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(evaluationRequest)
    .expect(200);
  return response.body;
}

/**
 * Call AuthZen API expecting an error
 * @param domainHrid - Domain HRID (path), not the UUID
 * @param accessToken
 * @param evaluationRequest
 * @param expectedStatus
 */
export async function evaluateAccessExpectError(
  domainHrid: string,
  accessToken: string,
  evaluationRequest: any,
  expectedStatus: number,
): Promise<any> {
  return await request(process.env.AM_GATEWAY_URL)
    .post(`/${domainHrid}/access/v1/evaluation`)
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(evaluationRequest)
    .expect(expectedStatus);
}

/**
 * Call AuthZen API without authentication
 * @param domainHrid - Domain HRID (path), not the UUID
 * @param evaluationRequest
 * @param expectedStatus
 */
export async function evaluateAccessUnauthenticated(
  domainHrid: string,
  evaluationRequest: AuthZenAccessEvaluationRequest,
  expectedStatus: number,
): Promise<any> {
  return await request(process.env.AM_GATEWAY_URL)
    .post(`/${domainHrid}/access/v1/evaluation`)
    .set('Content-Type', 'application/json')
    .send(evaluationRequest)
    .expect(expectedStatus);
}
