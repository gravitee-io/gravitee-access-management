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
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Observable } from 'rxjs';

import { GroupService } from '../services/group.service';
import { OrganizationService } from '../services/organization.service';

@Injectable()
export class GroupsResolver {
  private default_page = 0;
  private default_size = 100;

  constructor(
    private groupService: GroupService,
    private organizationService: OrganizationService,
  ) {}

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<any> {
    if (state.url.startsWith('/settings')) {
      return this.organizationService.groups(this.default_page, this.default_size);
    }
    const domainId = route.parent.data['domain'].id;
    return this.groupService.findByDomain(domainId, this.default_page, this.default_size);
  }
}
