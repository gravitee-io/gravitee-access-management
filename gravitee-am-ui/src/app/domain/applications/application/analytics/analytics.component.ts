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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import { DashboardData } from '../../../components/dashboard/dashboard.component';

@Component({
  selector: 'app-application-analytics',
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.scss']
})
export class ApplicationAnalyticsComponent implements OnInit {
  dashboard: DashboardData = {
    widgets: [
      {
        flex: 50,
        title: 'Logins',
        subhead: 'Latest logins',
        chart: {
          type: 'count',
          prefix: '+',
          request: {
            type: 'count',
            field: 'user_login'
          }
        }
      },
      {
        flex: 50,
        title: 'Sign ups',
        subhead: 'New sign ups',
        chart: {
          type: 'count',
          prefix: '+',
          request: {
            type: 'count',
            field: 'user_registered'
          }
        }
      },
      {
        flex: 100,
        title: 'Login Activity',
        chart: {
          type: 'line',
          request: {
            type: 'date_histo',
            field: 'user_login',
          }
        }
      },
      {
        flex: 100,
        title: 'Sign up Activity',
        chart: {
          type: 'line',
          request: {
            type: 'date_histo',
            field: 'user_registered',
          }
        }
      }
    ]
  };
  domainId: string;
  applicationId: string;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.applicationId = this.route.snapshot.params['appId'];
  }

}
