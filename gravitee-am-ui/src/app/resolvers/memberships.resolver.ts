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
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot } from "@angular/router";
import {Observable, of} from "rxjs";
import { DomainService } from "../services/domain.service";
import { ApplicationService } from "../services/application.service";
import {catchError} from "rxjs/operators";
import {OrganizationService} from "../services/organization.service";

@Injectable()
export class MembershipsResolver implements Resolve<any> {

  constructor(private organizationService: OrganizationService,
              private domainService: DomainService,
              private applicationService: ApplicationService) { }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<any>|Promise<any>|any {

    if (state.url.startsWith('/settings')) {
      return this.organizationService.members()
        .pipe(
          catchError(__ => {
            return of({});
          })
        );
    }

    const domainId = route.paramMap.get('domainId');
    const appId = route.paramMap.get('appId');

    if (appId) {
      return this.applicationService.members(domainId, appId);
    } else {
      return this.domainService.members(domainId);
    }
  }

}
