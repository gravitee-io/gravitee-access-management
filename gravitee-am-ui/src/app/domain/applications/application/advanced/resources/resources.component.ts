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
import {ApplicationService} from '../../../../../services/application.service';

@Component({
  selector: 'app-application-resources',
  templateUrl: './resources.component.html',
  styleUrls: ['./resources.component.scss']
})
export class ApplicationResourcesComponent implements OnInit {
  private domainId: string;
  application: any;
  resources: any[];
  page: any = {};

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.params['domainId'];
    this.application = this.route.snapshot.data['application'];
    const pagedResources = this.route.snapshot.data['resources'];
    this.resources = pagedResources.data;
    this.page.totalElements = pagedResources.totalCount;
  }

  loadResources() {
    this.applicationService.resources(this.domainId, this.application.id, this.page.pageNumber, this.page.size).subscribe(pagedResources => {
      this.page.totalElements = pagedResources.totalCount;
      this.resources = pagedResources.data;
    });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadResources();
  }
}
