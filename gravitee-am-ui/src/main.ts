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
import { enableProdMode } from '@angular/core';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { forkJoin, Observable } from 'rxjs';
import { loadDefaultTranslations } from '@gravitee/ui-components/src/lib/i18n';
import { LicenseConfiguration } from '@gravitee/ui-particles-angular';

import { AppModule } from './app/app.module';
import { environment } from './environments/environment';
import { AppConfig } from './config/app.config';
import { FeatureInfoData } from './app/components/gio-license/gio-license-data';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/mode/markdown/markdown';

if (environment.production) {
  enableProdMode();
}

loadDefaultTranslations().then((_) => ({}));

const constants = new Observable((observer) => {
  fetch('constants.json', { method: 'get' }).then((response) => {
    response.json().then((data) => {
      observer.next(data);
      observer.complete();
    });
  });
});

const build = new Observable((observer) => {
  fetch('build.json', { method: 'get' }).then((response) => {
    response.json().then((data) => {
      observer.next(data);
      observer.complete();
    });
  });
});

forkJoin([constants, build]).subscribe((response) => {
  const DEFAULT_ORGANIZATION = ':organizationId';
  const DEFAULT_ENV = ':environmentId';
  const PORTAL_TITLE = 'Access Management';
  Object.keys(response[0]).forEach((key) => (AppConfig.settings[key] = response[0][key]));
  Object.keys(response[1]).forEach((key) => (AppConfig.settings[key] = response[1][key]));
  AppConfig.settings.portalTitle = PORTAL_TITLE;
  AppConfig.settings.organizationBaseURL = AppConfig.settings.baseURL + '/organizations/' + DEFAULT_ORGANIZATION;

  AppConfig.settings.environmentBaseURL = AppConfig.settings.organizationBaseURL + '/environments/' + DEFAULT_ENV;
  AppConfig.settings.domainBaseURL = AppConfig.settings.organizationBaseURL + '/environments/' + DEFAULT_ENV + '/domains/';
  const resourceURL = `${AppConfig.settings.baseURL}/platform/license`;
  const trialResourceURL = 'https://gravitee.io/self-hosted-trial';
  const featureInfoData = FeatureInfoData;
  const licenseConfiguration: LicenseConfiguration = {
    resourceURL,
    featureInfoData,
    trialResourceURL,
    utmSource: 'oss_am',
    utmCampaign: 'oss_am_to_ee_am',
  };
  platformBrowserDynamic([{ provide: 'LicenseConfiguration', useValue: licenseConfiguration }]).bootstrapModule(AppModule);
});
