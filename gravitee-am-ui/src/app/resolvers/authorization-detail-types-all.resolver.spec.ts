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
import { ActivatedRouteSnapshot } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthorizationDetailTypeService } from '../services/authorization-detail-type.service';

import { AuthorizationDetailTypesAllResolver } from './authorization-detail-types-all.resolver';

describe('AuthorizationDetailTypesAllResolver', () => {
  const route = { parent: { data: { domain: { id: 'domain-id' } } } } as unknown as ActivatedRouteSnapshot;

  function resolveWith(listAll: jest.Mock) {
    const resolver = new AuthorizationDetailTypesAllResolver({ listAll } as unknown as AuthorizationDetailTypeService);
    let result: any[] | null | undefined;
    resolver.resolve(route).subscribe((value) => (result = value));
    return result;
  }

  it('should resolve every type of the domain', () => {
    const types = [{ type: 'payment_initiation' }];
    const listAll = jest.fn().mockReturnValue(of(types));

    expect(resolveWith(listAll)).toBe(types);
    expect(listAll).toHaveBeenCalledWith('domain-id');
  });

  it('should resolve null when the types cannot be listed', () => {
    expect(resolveWith(jest.fn().mockReturnValue(throwError(() => ({ status: 403 }))))).toBeNull();
  });
});
