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
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable } from 'rxjs';
import { map, mergeMap } from 'rxjs/operators';

import { DomainService } from '../services/domain.service';
import { NavbarService } from '../components/navbar/navbar.service';

@Injectable()
export class DomainResolver implements Resolve<any> {
  constructor(private domainService: DomainService, private navbarService: NavbarService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<any> {
    const domainHrid = route.paramMap.get('domainId');

    return this.domainService.get(domainHrid).pipe(
      mergeMap((domain) =>
        this.domainService.permissions(domain.id).pipe(
          map((__) => {
            this.navbarService.notifyDomain(domain);
            return domain;
          }),
        ),
      ),
    );
  }
}
