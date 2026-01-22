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
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ExtensionGrantService } from '../services/extension-grant.service';

@Injectable()
export class DomainGrantTypesResolver {
  constructor(private extensionGrantService: ExtensionGrantService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<any> {
    const domainId = route.parent.data['domain'].id;
    return this.extensionGrantService.findByDomain(domainId).pipe(
      map((grantTypes) => {
        return grantTypes.map((grantType) => {
          return {
            name: grantType.name || grantType.grantType,
            value: grantType.grantType,
          };
        });
      }),
    );
  }
}
