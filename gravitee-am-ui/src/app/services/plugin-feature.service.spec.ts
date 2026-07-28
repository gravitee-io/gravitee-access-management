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
import { firstValueFrom, of } from 'rxjs';
import { GioLicenseService } from '@gravitee/ui-particles-angular';

import { OrganizationService } from './organization.service';
import { PluginFeatureService } from './plugin-feature.service';

type Factor = { id: string; feature?: string; deployed?: boolean };

describe('PluginFeatureService', () => {
  const factors: Factor[] = [
    { id: 'sms-am-factor', feature: 'am-mfa-sms', deployed: true },
    { id: 'otp-am-factor', feature: '', deployed: true },
    { id: 'legacy-am-factor', deployed: false },
  ];

  let factorsSpy: jest.Mock;
  let isMissingFeature$: jest.Mock;
  let service: PluginFeatureService;

  beforeEach(() => {
    factorsSpy = jest.fn().mockReturnValue(of(factors));
    // Mirrors GioLicenseService: only 'am-mfa-sms' is missing from the effective license.
    isMissingFeature$ = jest.fn().mockImplementation((feature?: string) => of(feature === 'am-mfa-sms'));
    const organizationService = { factors: factorsSpy } as unknown as OrganizationService;
    const licenseService = { isMissingFeature$ } as unknown as GioLicenseService;
    service = new PluginFeatureService(organizationService, licenseService);
  });

  describe('getFeature$', () => {
    it('resolves the plugin feature for a known type', async () => {
      expect(await firstValueFrom(service.getFeature$('factor', 'sms-am-factor'))).toBe('am-mfa-sms');
    });

    it('treats a blank feature as OSS (undefined)', async () => {
      expect(await firstValueFrom(service.getFeature$('factor', 'otp-am-factor'))).toBeUndefined();
    });

    it('resolves undefined for an unknown or empty type', async () => {
      expect(await firstValueFrom(service.getFeature$('factor', 'does-not-exist'))).toBeUndefined();
      expect(await firstValueFrom(service.getFeature$('factor', ''))).toBeUndefined();
    });

    it('caches the catalog across calls (fetched once)', async () => {
      await firstValueFrom(service.getFeature$('factor', 'sms-am-factor'));
      await firstValueFrom(service.getFeature$('factor', 'otp-am-factor'));
      expect(factorsSpy).toHaveBeenCalledTimes(1);
    });

    it('resolves social/enterprise IdPs by merging the built-in and social catalogs', async () => {
      const identities = jest.fn().mockReturnValue(of([{ id: 'ldap-am-idp', feature: 'am-idp-ldap' }]));
      const socialIdentities = jest.fn().mockReturnValue(of([{ id: 'azure-ad-am-idp', feature: 'am-idp-azure-ad' }]));
      const organizationService = { identities, socialIdentities } as unknown as OrganizationService;
      const idpService = new PluginFeatureService(organizationService, { isMissingFeature$ } as unknown as GioLicenseService);

      expect(await firstValueFrom(idpService.getFeature$('identity_provider', 'azure-ad-am-idp'))).toBe('am-idp-azure-ad');
      expect(await firstValueFrom(idpService.getFeature$('identity_provider', 'ldap-am-idp'))).toBe('am-idp-ldap');
    });
  });

  describe('isMissingFeatureForType$', () => {
    it('reports the missing feature via the license', async () => {
      expect(await firstValueFrom(service.isMissingFeatureForType$('factor', 'sms-am-factor'))).toBe(true);
      expect(isMissingFeature$).toHaveBeenCalledWith('am-mfa-sms');
    });

    it('is false for an OSS plugin (no feature required)', async () => {
      expect(await firstValueFrom(service.isMissingFeatureForType$('factor', 'otp-am-factor'))).toBe(false);
    });
  });

  describe('decorateCatalog$', () => {
    it('gates each item on its declared feature via the license', async () => {
      const decorated = await firstValueFrom(service.decorateCatalog$(factors));
      expect(decorated[0].licenseOptions).toEqual({ feature: 'am-mfa-sms' });
      expect(await firstValueFrom(decorated[0].isMissing$)).toBe(true);
      expect(await firstValueFrom(decorated[1].isMissing$)).toBe(false); // blank feature normalised to OSS
      expect(await firstValueFrom(decorated[2].isMissing$)).toBe(false); // no feature
    });
  });
});
