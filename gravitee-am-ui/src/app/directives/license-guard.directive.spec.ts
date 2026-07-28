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
import { ElementRef } from '@angular/core';
import { of } from 'rxjs';
import { GioLicenseService } from '@gravitee/ui-particles-angular';

import { AmFeature } from '../components/gio-license/gio-license-data';

import { LicenseGuardDirective } from './license-guard.directive';

describe('LicenseGuardDirective', () => {
  let element: HTMLButtonElement;
  let openDialog: jest.Mock;
  let isMissingFeature$: jest.Mock;
  let directive: LicenseGuardDirective;

  const setup = (missing: boolean) => {
    element = document.createElement('button');
    document.body.appendChild(element);
    openDialog = jest.fn();
    isMissingFeature$ = jest.fn().mockReturnValue(of(missing));
    const licenseService = { isMissingFeature$, openDialog } as unknown as GioLicenseService;
    directive = new LicenseGuardDirective(licenseService, { nativeElement: element } as ElementRef);
    directive.licenseOptions = { feature: 'am-mfa-sms' };
    directive.ngOnInit();
    directive.ngOnChanges();
  };

  afterEach(() => {
    directive.ngOnDestroy();
    element.remove();
  });

  it('opens the upgrade dialog and blocks the host click when the feature is missing', () => {
    setup(true);
    const hostClick = jest.fn();
    element.addEventListener('click', hostClick);
    element.click();
    expect(openDialog).toHaveBeenCalledWith({ feature: 'am-mfa-sms' }, expect.anything());
    expect(hostClick).not.toHaveBeenCalled();
  });

  it('falls back to a generic upsell dialog when the feature has no FeatureInfoData entry', () => {
    setup(true);
    openDialog.mockImplementationOnce(() => {
      throw new Error('Unknown Feature value');
    });
    const hostClick = jest.fn();
    element.addEventListener('click', hostClick);
    element.click();
    expect(openDialog).toHaveBeenCalledTimes(2);
    expect(openDialog).toHaveBeenLastCalledWith({ feature: AmFeature.AM_ENTERPRISE }, expect.anything());
    expect(hostClick).not.toHaveBeenCalled(); // still blocked
  });

  it('lets the host click through when the feature is granted', () => {
    setup(false);
    const hostClick = jest.fn();
    element.addEventListener('click', hostClick);
    element.click();
    expect(openDialog).not.toHaveBeenCalled();
    expect(hostClick).toHaveBeenCalled();
  });

  it('re-evaluates when the license options resolve asynchronously', () => {
    setup(false); // initially not missing (e.g. before the feature is resolved)
    isMissingFeature$.mockReturnValue(of(true));
    directive.licenseOptions = { feature: 'am-mfa-call' };
    directive.ngOnChanges();

    const hostClick = jest.fn();
    element.addEventListener('click', hostClick);
    element.click();
    expect(openDialog).toHaveBeenCalled();
    expect(hostClick).not.toHaveBeenCalled();
  });
});
