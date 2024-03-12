import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { MfaTestContext } from './mfa-setup-fixture';
import { initiateLoginFlow } from '@gateway-commands/login-commands';

export async function logoutUser(testContext: MfaTestContext) {
  if (testContext.auth) {
    performGet(testContext.oidc.logoutEndpoint, '', { Cookie: testContext.auth.cookie });
    testContext = null;
  }
}
