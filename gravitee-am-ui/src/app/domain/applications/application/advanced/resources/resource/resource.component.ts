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
import {ApplicationService} from '../../../../../../services/application.service';

@Component({
  selector: 'app-application-resource',
  templateUrl: './resource.component.html',
  styleUrls: ['./resource.component.scss']
})
export class ApplicationResourceComponent implements OnInit {
  resource: any;
  policies: any[];

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService) {
  }

  ngOnInit(): void {
    this.resource = this.route.snapshot.data['resource'];
    this.applicationService.resourcePolicies(this.resource.domain, this.resource.clientId, this.resource.id)
        .subscribe(response => this.policies = response);
  }
}
