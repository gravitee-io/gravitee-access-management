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
import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Route } from '@angular/router';
import { Observable, of } from 'rxjs';
import { GioLicenseService, LicenseOptions } from '@gravitee/ui-particles-angular';
import { map } from 'rxjs/operators';

@Injectable()
export class LicenseGuard implements CanActivate {
  constructor(private gioLicenseService: GioLicenseService) {}
  canActivate(route: ActivatedRouteSnapshot): Observable<boolean> {
    return this.isMissingFeature$(route).pipe(map((isMissingFeature) => !isMissingFeature));
  }

  isMissingFeature$(route: ActivatedRouteSnapshot | Route): Observable<boolean> {
    const licenseOptions = this.getLicenseOptions(route);
    if (licenseOptions === undefined) {
      return of(false);
    }
    return this.gioLicenseService.isMissingFeature$(licenseOptions);
  }

  getLicenseOptions(route: ActivatedRouteSnapshot | Route): LicenseOptions {
    if (!route.data || !route.data.licenseOptions) {
      return undefined;
    }
    return route.data.licenseOptions;
  }
}
