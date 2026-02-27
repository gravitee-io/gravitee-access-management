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

import { EntityStoreService } from '../services/entity-store.service';

@Injectable()
export class EntityStoreResolver {
  constructor(private entityStoreService: EntityStoreService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<any> {
    let parent = route.parent;
    while (parent && !parent.data['domain']) {
      parent = parent.parent;
    }
    const domainId = parent?.data['domain']?.id;
    const entityStoreId = route.paramMap.get('entityStoreId');
    return this.entityStoreService.get(domainId, entityStoreId);
  }
}
