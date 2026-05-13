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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ApplicationService } from '../../services/application.service';

@Component({
  selector: 'app-agents',
  templateUrl: './agents.component.html',
  styleUrls: ['./agents.component.scss'],
  standalone: false,
})
export class AgentsComponent implements OnInit {
  agents: any[];
  private searchValue: string;
  domainId: string;
  page: any = {};

  constructor(
    private applicationService: ApplicationService,
    private route: ActivatedRoute,
  ) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    const pagedAgents = this.route.snapshot.data['applications'];
    this.agents = pagedAgents.data;
    this.page.totalElements = pagedAgents.totalCount;
  }

  onSearch(event) {
    this.page.pageNumber = 0;
    this.searchValue = event.target.value;
    this.loadAgents();
  }

  loadAgents() {
    const find = this.searchValue
      ? this.applicationService.search(this.domainId, '*' + this.searchValue + '*', 'AGENT')
      : this.applicationService.findByDomain(this.domainId, this.page.pageNumber, this.page.size, 'AGENT');

    find.subscribe((pagedAgents) => {
      this.page.totalElements = pagedAgents.totalCount;
      this.agents = pagedAgents.data;
    });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadAgents();
  }

  get isEmpty() {
    return !this.agents || (this.agents.length === 0 && !this.searchValue);
  }
}
