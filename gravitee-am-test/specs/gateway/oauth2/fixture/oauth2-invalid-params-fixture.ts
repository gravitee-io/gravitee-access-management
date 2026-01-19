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
export function getParamsInvalidAuthorizeRequests(clientId: string) {
  return [
    {
      title: 'Unsupported response type',
      params: '?response_type=unknown_response_type',
      uri: '/oauth/error',
      error: 'unsupported_response_type',
      error_description: 'Unsupported+response+type%3A+unknown_response_type',
    },
    {
      title: 'Duplicated query params',
      params: '?response_type=unknown_response_type&response_type=unknown_response_type',
      uri: '/oauth/error',
      error: 'invalid_request',
      error_description: 'Parameter+%5Bresponse_type%5D+is+included+more+than+once',
    },
    {
      title: 'Missing client id',
      params: '?response_type=code',
      uri: '/oauth/error',
      error: 'invalid_request',
      error_description: 'Missing+parameter%3A+client_id',
    },
    {
      title: 'Invalid client id',
      params: '?response_type=code&client_id=wrong-client-id',
      uri: '/oauth/error',
      error: 'invalid_request',
      error_description: 'No+client+found+for+client_id+wrong-client-id',
    },
    {
      title: 'Send a redirect_uri not configured in the client',
      params: `?response_type=code&client_id=${clientId}&redirect_uri=http://my_bad_host:4000`,
      uri: `/oauth/error`,
      error: 'redirect_uri_mismatch',
      error_description: 'The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application',
    },
    {
      title: 'Send a bad redirect_uri strict matching',
      patchDomain: true,
      params: `?response_type=code&client_id=${clientId}&redirect_uri=http://my_bad_host:4000?extraParam=test`,
      uri: `/oauth/error`,
      error: 'redirect_uri_mismatch',
      error_description: 'The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application',
    },
    {
      title: 'Error with state parameters',
      params: `?response_type=code&client_id=${clientId}&redirect_uri=http://my_bad_host:4000&state=xxx-yyy-zzz`,
      uri: `/oauth/error`,
      error: 'redirect_uri_mismatch',
      error_description: 'The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application',
      state: 'xxx-yyy-zzz',
    },
  ];
}
