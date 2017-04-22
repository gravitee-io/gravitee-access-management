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
import { DomainService } from "../services/domain.service";

@Component({
  selector: 'app-domains',
  templateUrl: './domains.component.html',
  styleUrls: ['./domains.component.scss']
})
export class DomainsComponent implements OnInit {
  title = 'Gravitee.io AM Portal';
  version = '0.0.1-SNAPSHOT';
  domains = [];

  constructor(private domainService : DomainService) { }

  ngOnInit() {
    this.domainService.list().subscribe(response => this.domains = response.json());
  }

  searchDomains(value) {
    console.log(value);
  }

  get isEmpty() {
    return !this.domains || this.domains.length == 0;
  }
}
