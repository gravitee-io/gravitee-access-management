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

import { OrganizationService } from '../../../../../services/organization.service';
import { ReporterService } from '../../../../../services/reporter.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { PluginFeatureService } from '../../../../../services/plugin-feature.service';

import { ReporterComponent } from './reporter.component';

describe('ReporterComponent', () => {
  let component: ReporterComponent;

  function build(routeData: any) {
    const route = { snapshot: { data: routeData } } as unknown as ActivatedRoute;
    const router = { routerState: { snapshot: { url: '/domains/a-domain/settings/audits/settings' } } } as unknown as Router;
    const organizationService = {
      reporterSchema: jest.fn().mockReturnValue(of({ properties: {} })),
    } as unknown as OrganizationService;
    const pluginFeatureService = {
      getFeature$: jest.fn().mockReturnValue(of('reporter')),
      isMissingFeatureForType$: jest.fn().mockReturnValue(of(false)),
    } as unknown as PluginFeatureService;

    const reporter = new ReporterComponent(
      route,
      router,
      organizationService,
      {} as ReporterService,
      {} as SnackbarService,
      {} as DialogService,
      pluginFeatureService,
    );
    reporter.form = { pristine: false, valid: true, reset: jest.fn() };
    return reporter;
  }

  function editing(reporter: any) {
    return build({
      organizationContext: false,
      createMode: false,
      domain: { id: 'a-domain' },
      reporterPlugins: [{ id: 'reporter-am-file' }, { id: 'reporter-am-kafka' }, { id: 'reporter-am-tcp' }],
      reporter,
    });
  }

  describe('attribute mapping support', () => {
    it.each([['reporter-am-kafka'], ['reporter-am-tcp'], ['reporter-am-file']])('offers the section for %s', (type) => {
      component = editing({ type, name: 'a-reporter' });
      component.ngOnInit();

      expect(component.supportsAttributeMappings()).toBe(true);
    });

    it('withholds the section from a reporter whose payload would drop the attributes', () => {
      component = editing({ type: 'reporter-am-jdbc', name: 'a-reporter' });
      component.ngOnInit();

      expect(component.supportsAttributeMappings()).toBe(false);
    });

    it('withholds the section from a system reporter, which the API refuses mappings on', () => {
      component = editing({ type: 'reporter-am-file', name: 'a-reporter', system: true });
      component.ngOnInit();

      expect(component.supportsAttributeMappings()).toBe(false);
    });
  });

  describe('seeding', () => {
    it('seeds the section from the stored reporter', () => {
      component = editing({
        type: 'reporter-am-file',
        name: 'a-reporter',
        attributeMappings: [{ exportedName: 'user_sub', expression: 'a' }],
        attributeMappingEventTypes: ['USER_LOGIN'],
      });

      component.ngOnInit();

      expect(component.attributeMappingsSeed).toEqual({
        mappings: [{ exportedName: 'user_sub', expression: 'a' }],
        eventTypes: ['USER_LOGIN'],
      });
    });

    it('seeds an unconfigured reporter with nothing', () => {
      component = editing({ type: 'reporter-am-file', name: 'a-reporter' });

      component.ngOnInit();

      expect(component.attributeMappingsSeed).toEqual({ mappings: [], eventTypes: [] });
    });
  });

  describe('changes', () => {
    beforeEach(() => {
      component = editing({ type: 'reporter-am-file', name: 'a-reporter' });
      component.ngOnInit();
    });

    it('carries the section changes onto the reporter it saves', () => {
      component.onAttributeMappingsChanged({
        mappings: [{ exportedName: 'user_sub', expression: 'a' }],
        eventTypes: ['USER_LOGIN'],
        isValid: true,
      });

      expect(component.reporter.attributeMappings).toEqual([{ exportedName: 'user_sub', expression: 'a' }]);
      expect(component.reporter.attributeMappingEventTypes).toEqual(['USER_LOGIN']);
      expect(component.readyToSave()).toBe(true);
    });

    it('blocks the save while the section is invalid', () => {
      component.onAttributeMappingsChanged({ mappings: [], eventTypes: ['USER_LOGIN'], isValid: false });

      expect(component.readyToSave()).toBe(false);
    });
  });

  describe('changing the reporter type while creating', () => {
    function creating() {
      return build({
        organizationContext: false,
        createMode: true,
        domain: { id: 'a-domain' },
        reporterPlugins: [{ id: 'reporter-am-file' }],
      });
    }

    it('drops mappings that the newly chosen type would not export', () => {
      component = creating();
      component.ngOnInit();
      component.onAttributeMappingsChanged({
        mappings: [{ exportedName: 'user_sub', expression: 'a' }],
        eventTypes: ['USER_LOGIN'],
        isValid: true,
      });

      component.onReporterTypeChanged({ value: 'reporter-am-jdbc' });

      expect(component.reporter.attributeMappings).toEqual([]);
      expect(component.reporter.attributeMappingEventTypes).toEqual([]);
      expect(component.attributeMappingsSeed).toEqual({ mappings: [], eventTypes: [] });
    });

    it('keeps mappings when the newly chosen type exports them', () => {
      component = creating();
      component.ngOnInit();
      component.onAttributeMappingsChanged({
        mappings: [{ exportedName: 'user_sub', expression: 'a' }],
        eventTypes: [],
        isValid: true,
      });

      component.onReporterTypeChanged({ value: 'reporter-am-tcp' });

      expect(component.reporter.attributeMappings).toEqual([{ exportedName: 'user_sub', expression: 'a' }]);
    });
  });
});
