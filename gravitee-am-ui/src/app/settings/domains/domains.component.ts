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
import { Component, OnInit } from "@angular/core";
import { AppConfig } from "../../../config/app.config";
import { ActivatedRoute } from "@angular/router";
import { DomainService } from "../../services/domain.service";

@Component({
  selector: "app-domains",
  templateUrl: "./domains.component.html",
  styleUrls: ["./domains.component.scss"],
})
export class DomainsComponent implements OnInit {
  private searchValue: string;
  page: any = {};
  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  domains = [];

  constructor(private route: ActivatedRoute,
              private domainService: DomainService) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    const pagedDomains = this.route.snapshot.data['domains'];
    this.domains = pagedDomains.data;
    this.page.totalElements = pagedDomains.totalCount
  }

  onSearch(event) {
    this.searchValue = event.target.value;
    this.loadDomains();
  }

  loadDomains() {
    const findDomains = (this.searchValue) ?
      this.domainService.search('*' + this.searchValue + '*', this.page.pageNumber, this.page.size) :
      this.domainService.findByEnvironment(this.page.pageNumber, this.page.size);

    findDomains.subscribe(pagedDomains => {
      this.page.totalElements = pagedDomains.totalCount;
      this.domains = pagedDomains.data;
    });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadDomains();
  }

  get isEmpty() {
    return !this.domains || this.domains.length === 0 && !this.searchValue;
  }
}
