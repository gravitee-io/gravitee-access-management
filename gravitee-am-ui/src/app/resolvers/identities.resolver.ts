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
import { Resolve } from '@angular/router';
import { Observable } from 'rxjs';

import { OrganizationService } from '../services/organization.service';

@Injectable()
export class IdentitiesResolver implements Resolve<any> {
  constructor(private organizationService: OrganizationService) {}

  resolve(): Observable<any> | Promise<any> | any {
    return Promise.all([
      this.organizationService.identities(true, true, true).toPromise(),
      this.organizationService
        .socialIdentities(true, true, true)
        .toPromise()
        .then((response) =>
          response.map((idp) => {
            idp.external = true;
            return idp;
          }),
        ),
    ]).then(([a, b]) =>
      [...a, ...b].reduce((map, idp) => {
        map[idp.id] = idp;
        return map;
      }, {}),
    );
  }
}
