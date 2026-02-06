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


/**
 * OAuth 2.0 token introspection result
 * 
 * Note: This interface represents the standard introspection response.
 * Additional fields may be present depending on the token type and AM configuration.
 */
export interface IntrospectionResult {
  active: boolean;
  client_id?: string;
  sub?: string;
  exp?: number;
  iat?: number;
  aud?: string | string[];
  scope?: string;
  [key: string]: unknown;
}

export interface AuthZenSubject {
  type: string;
  id: string;
  properties?: Record<string, unknown>;
}

export interface AuthZenResource {
  type: string;
  id: string;
  properties?: Record<string, unknown>;
}

export interface AuthZenAction {
  name: string;
  properties?: Record<string, unknown>;
}

export interface AuthZenRequest {
  subject: AuthZenSubject;
  resource: AuthZenResource;
  action: AuthZenAction;
  context?: Record<string, unknown>;
}

export interface AuthZenResponse {
  decision: boolean; // true = PERMIT, false = DENY
  context?: Record<string, unknown>;
}
