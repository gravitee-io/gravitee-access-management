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

import { ProtectedResourceService } from '../services/protected-resource.service';

@Injectable()
export class McpServerPermissionsResolver {
  constructor(private service: ProtectedResourceService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<any> {
    const domainId = route.parent.data['domain'].id;
    const id = route.paramMap.get('mcpServerId');
    return this.service.permissions(domainId, id);
  }
}
