import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { ResponseError } from '../../../api/management/runtime';

export async function get(uri: string, expectedStatus: number, headers: any = null, expectedLocation: string = null) {
  const response = await performGet(uri, '', headers).expect(expectedStatus);
  if (response?.headers['location']?.includes('error')) {
    throw new ResponseError(response, 'error in Location');
  }
  if (expectedLocation && !response?.headers['location']?.includes(expectedLocation)) {
    throw new ResponseError(response, `expectedLocation=${expectedLocation}, Location=${response?.headers['location']}`);
  }
  return response;
}

export async function followUpGet(response, expectedStatus: number, expectedLocation: string = null) {
  const headers = {
    ...response.headers,
    Cookie: response.headers['set-cookie'],
  };
  return get(response.headers['location'], expectedStatus, headers, expectedLocation);
}
