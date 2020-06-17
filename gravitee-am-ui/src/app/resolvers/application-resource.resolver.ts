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
import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, Resolve, RouterStateSnapshot} from '@angular/router';
import {ApplicationService} from '../services/application.service';
import {Observable} from 'rxjs';

@Injectable()
export class ApplicationResourceResolver implements Resolve<any> {

  constructor(private applicationService: ApplicationService) { }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<any>|Promise<any>|any {
    const domainId = route.parent.parent.parent.paramMap.get('domainId');
    const appId = route.parent.parent.paramMap.get('appId');
    const resourceId = route.paramMap.get('resourceId');
    return this.applicationService.resource(domainId, appId, resourceId);
  }
}
