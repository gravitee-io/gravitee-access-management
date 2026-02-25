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
import { ActivatedRouteSnapshot } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthorizationDataService } from '../services/authorization-data.service';

@Injectable()
export class AuthorizationDataResolver {
  constructor(private authorizationDataService: AuthorizationDataService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<any> {
    let parent = route.parent;
    while (parent && !parent.data['domain']) {
      parent = parent.parent;
    }
    const domainId = parent?.data['domain']?.id;
    return this.authorizationDataService.findByDomain(domainId).pipe(catchError(() => of(null)));
  }
}
