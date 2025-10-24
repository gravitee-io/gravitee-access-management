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
import { SortType } from '@swimlane/ngx-datatable';

import { Page, Sort } from '../../services/api.model';

import { McpServer, McpServersService } from './mcp-servers.service';

@Component({
  selector: 'app-mcp-servers',
  templateUrl: './mcp-servers.component.html',
  styleUrl: './mcp-servers.component.scss',
  standalone: false,
})
export class DomainMcpServersComponent implements OnInit {
  PAGE_SIZE = 10;
  domainId: string;
  page: Page<McpServer>;
  currentPage: number;
  sort: Sort = { dir: 'desc', prop: 'updatedAt' };

  constructor(
    private route: ActivatedRoute,
    private service: McpServersService,
  ) {
    this.currentPage = 0;
    this.page = {
      data: [],
      totalCount: 0,
      currentPage: 0,
    };
  }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.fetchData();
  }

  fetchData() {
    this.service.findByDomain(this.domainId, this.currentPage, this.PAGE_SIZE, this.sort).subscribe((page) => (this.page = page));
  }

  changePage(e: any) {
    this.currentPage = e.offset;
    this.fetchData();
  }

  applySort(e: any) {
    this.sort = e.sorts[0];
    this.fetchData();
  }

  get isEmpty(): boolean {
    return this.page.data?.length == 0;
  }

  protected readonly SortType = SortType;
}
