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
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { DashboardService } from "../services/dashboard.service";
import { ApplicationService } from "../services/application.service";

@Injectable()
export class ApplicationsResolver implements Resolve<any> {
  private default_page: number = 0;
  private default_size: number = 50;

  constructor(private applicationService: ApplicationService, private dashboardService: DashboardService) { }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<any>|Promise<any>|any {
    let domainId = route.paramMap.get('domainId');

    if (domainId) {
      return this.applicationService.findByDomain(domainId, this.default_page, this.default_size);
    } else {
      return this.dashboardService.findApplications(null);
    }
  }

}
