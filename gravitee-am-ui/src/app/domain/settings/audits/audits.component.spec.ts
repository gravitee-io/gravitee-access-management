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
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuditService } from '../../../services/audit.service';
import { OrganizationService } from '../../../services/organization.service';
import { EnvironmentService } from '../../../services/environment.service';
import { UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';

import { AuditsComponent } from './audits.component';

function organizationAudit(targetType: string) {
  return {
    type: `${targetType}_CREATED`,
    outcome: { status: 'success' },
    referenceType: 'organization',
    target: { id: 'target-id', type: targetType, referenceType: 'organization', displayName: 'A target' },
  };
}

describe('AuditsComponent', () => {
  let component: AuditsComponent;

  beforeEach(() => {
    const route = {
      snapshot: { data: {}, paramMap: { get: () => 'a-value' } },
    } as unknown as ActivatedRoute;
    const router = { routerState: { snapshot: { url: '/settings/audits' } } } as unknown as Router;
    const auditService = { search: jest.fn().mockReturnValue(of({ totalCount: 0, data: [] })) } as unknown as AuditService;
    const organizationService = { auditEventTypes: jest.fn().mockReturnValue(of([])) } as unknown as OrganizationService;
    const userService = { search: jest.fn().mockReturnValue(of({ data: [] })) } as unknown as UserService;
    const authService = { hasPermissions: jest.fn().mockReturnValue(true) } as unknown as AuthService;

    component = new AuditsComponent(route, router, auditService, organizationService, {} as EnvironmentService, userService, authService);
  });

  it.each(['ENVIRONMENT', 'DATA_PLANE', 'ENTRYPOINT', 'MEMBERSHIP'])('does not link a %s target', (targetType) => {
    const audit = organizationAudit(targetType);

    expect(component.getTargetUrl(audit)).toEqual([]);
    expect(component.hasLinkableTarget(audit)).toBe(false);
  });

  it('still links a target the console has a page for', () => {
    const audit = organizationAudit('FORM');

    expect(component.hasLinkableTarget(audit)).toBe(true);
    expect(component.getTargetUrl(audit)).toEqual(['/settings', 'forms', 'form']);
  });
});
