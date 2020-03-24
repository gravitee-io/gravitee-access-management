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
import {Observable, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {FormService} from '../services/form.service';
import {PlatformService} from '../services/platform.service';

@Injectable()
export class FormResolver implements Resolve<any> {

  constructor(private formService: FormService, private platformService: PlatformService) { }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<any>|Promise<any>|any {
    if (state.url.startsWith('/settings')) {
      return this.platformService.forms('LOGIN')
        .pipe(
          catchError(__ => {
            return of({});
          })
        );
    }
    const domainId = route.parent.paramMap.get('domainId') ? route.parent.paramMap.get('domainId') : route.parent.parent.paramMap.get('domainId') ? route.parent.parent.paramMap.get('domainId') : route.parent.parent.parent.paramMap.get('domainId');
    const appId = route.parent.parent.paramMap.get('appId');
    const template = route.queryParams['template'];
    return this.formService.get(domainId, appId, template)
      .pipe(
        catchError(__ => {
          return of({});
        })
      );
  }

}
