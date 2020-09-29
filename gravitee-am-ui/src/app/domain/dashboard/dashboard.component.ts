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

@Component({
  selector: 'app-domain-overview',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DomainDashboardComponent implements OnInit {
  domain: any = {};
  dashboard: any = {
    widgets: [
      {
        flex: 25,
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
        flex: 25,
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
        flex: 25,
        title: 'Users',
        subhead: 'Total users',
        chart: {
          type: 'count',
          request: {
            type: 'count',
            field: 'user'
          }
        }
      },
      {
        flex: 25,
        title: 'Applications',
        subhead: 'Total apps',
        chart: {
          type: 'count',
          request: {
            type: 'count',
            field: 'application'
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
      },
      {
        flex: 50,
        title: 'User status',
        subhead: 'User status repartition',
        chart: {
          type: 'pie',
          request: {
            type: 'group_by',
            field: 'user_status'
          }
        }
      },
      {
        flex: 50,
        title: 'User registration',
        subhead: 'User registration completion',
        chart: {
          type: 'gauge',
          request: {
            type: 'group_by',
            field: 'user_registration'
          }
        }
      },
      {
        flex: 100,
        title: 'Top applications',
        subhead: 'Ordered by login calls',
        chart: {
          type: 'table',
          columns: ['Application', 'Logins'],
          paging: 5,
          request: {
            type: 'group_by',
            field: 'application',
            size: 20
          }
        }
      },
    ]
  };

  constructor(private route: ActivatedRoute) { }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
  }
}
