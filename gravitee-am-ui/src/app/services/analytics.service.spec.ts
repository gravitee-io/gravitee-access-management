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
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';

import { AnalyticsService } from './analytics.service';
import { AppConfig } from '../../config/app.config';

describe('AnalyticsService', () => {
  let httpTestingController: HttpTestingController;
  let analyticsService: AnalyticsService;

  const analyticsResponse = { value: 1234 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AnalyticsService],
      imports: [HttpClientTestingModule],
    });

    httpTestingController = TestBed.get(HttpTestingController);
    analyticsService = TestBed.get(AnalyticsService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('calls domain analytics endpoint', (done) => {
    const domainId = 'domain-1234';
    const type = 'count';
    const field = 'application';
    const interval = 12;
    const from = 0;
    const to = 100;
    const size = 20;

    analyticsService.search(domainId, { type, field, interval, from, to, size})
      .subscribe(result => {
        expect(result.value).toEqual(analyticsResponse.value);
        done();
      });

    httpTestingController
      .expectOne({
        method: 'GET',
        url: `${AppConfig.settings.domainBaseURL}${domainId}/analytics?type=${type}&field=${field}&interval=${interval}&from=${from}&to=${to}&size=${size}`,
      })
      .flush(analyticsResponse);

  });

  it('calls domain analytics endpoint with optional params', (done) => {
    const domainId = 'domain-1234';
    const type = 'count';
    const field = 'application';
    const interval = 12;
    const size = 20;

    analyticsService.search(domainId, {type, field, interval, from: undefined, to: undefined, size})
      .subscribe(result => {
        expect(result.value).toEqual(analyticsResponse.value);
        done();
      });

    httpTestingController
      .expectOne({
        method: 'GET',
        url: `${AppConfig.settings.domainBaseURL}${domainId}/analytics?type=${type}&field=${field}&interval=${interval}&size=${size}`,
      })
      .flush(analyticsResponse);
  });

  it('calls application analytics endpoint', (done) => {
    const domainId = 'domain-1234';
    const applicationId = 'app-1234';
    const type = 'count';
    const field = 'application';
    const interval = 12;
    const from = 0;
    const to = 100;
    const size = 20;

    analyticsService.searchApplicationAnalytics(domainId, applicationId, { type, field, interval, from, to, size})
      .subscribe(result => {
        expect(result.value).toEqual(analyticsResponse.value);
        done();
      });

    httpTestingController
      .expectOne({
        method: 'GET',
        url: `${AppConfig.settings.domainBaseURL}${domainId}/applications/${applicationId}/analytics?type=${type}&field=${field}&interval=${interval}&from=${from}&to=${to}&size=${size}`,
      })
      .flush(analyticsResponse);
  });

  it('calls application analytics endpoint with optional params', (done) => {
    const domainId = 'domain-1234';
    const applicationId = 'app-1234';
    const type = 'count';
    const field = 'application';
    const interval = 12;
    const size = 20;

    analyticsService.searchApplicationAnalytics(domainId, applicationId, {type, field, interval, from: undefined, to: undefined, size})
      .subscribe(result => {
        expect(result.value).toEqual(analyticsResponse.value);
        done();
      });

    httpTestingController
      .expectOne({
        method: 'GET',
        url: `${AppConfig.settings.domainBaseURL}${domainId}/applications/${applicationId}/analytics?type=${type}&field=${field}&interval=${interval}&size=${size}`,
      })
      .flush(analyticsResponse);
  });
});
