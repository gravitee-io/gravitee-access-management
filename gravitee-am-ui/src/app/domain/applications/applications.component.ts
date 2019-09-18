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
import { DialogService } from "../../services/dialog.service";
import { SnackbarService } from "../../services/snackbar.service";
import { ActivatedRoute } from "@angular/router";
import { ApplicationService } from "../../services/application.service";

@Component({
  selector: 'app-applications',
  templateUrl: './applications.component.html',
  styleUrls: ['./applications.component.scss']
})
export class ApplicationsComponent implements OnInit {
  private applications: any[];
  domainId: string;
  domain: any = {};
  newApplicationRouterLink: any[] = ['/dashboard', 'applications', 'new'];
  page: any = {};

  constructor(private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private applicationService: ApplicationService,
              private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    let pagedApps = this.route.snapshot.data['applications'];
    this.applications = pagedApps.data;
    this.page.totalElements = pagedApps.totalCount;
    if (this.domain) {
      this.newApplicationRouterLink = ['/domains', this.domain.id, 'applications', 'new'];
    }
  }

  loadApps() {
    this.applicationService.findByDomain(this.domainId, this.page.pageNumber, this.page.size).subscribe(pagedApps => {
      this.page.totalElements = pagedApps.totalCount;
      this.applications = pagedApps.data;
    });
  }

  setPage(pageInfo){
    this.page.pageNumber = pageInfo.offset;
    this.loadApps();
  }

  get isEmpty() {
    return !this.applications || this.applications.length === 0;
  }

  logoUrl(app) {
    return 'assets/application-type-icons/' + app.type.toLowerCase() + '.png';
  }
}
