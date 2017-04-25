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

import { AppModule } from './app/app.module';
import { environment } from './environments/environment';
import { AppConfig }  from "./config/app.config";
import { Observable } from 'rxjs/Rx';

if (environment.production) {
  enableProdMode();
}

let constants = Observable.create(observer => {
  fetch('constants.json', {method: 'get'}).then(response => {
    response.json().then(data => {
      observer.next(data);
      observer.complete();
    })
  })
});

let build = Observable.create(observer => {
  fetch('build.json', {method: 'get'}).then(response => {
    response.json().then(data => {
      observer.next(data);
      observer.complete();
    })
  })
});

Observable.forkJoin([constants, build])
  .subscribe((response) => {
    let config = {};
    Object.keys(response[0]).forEach((key) => config[key] = response[0][key]);
    Object.keys(response[1]).forEach((key) => config[key] = response[1][key]);
    AppConfig.settings = config;
    platformBrowserDynamic().bootstrapModule(AppModule);
  });
