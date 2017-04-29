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
import { ActivatedRoute } from "@angular/router";
import { DomainService } from "../../services/domain.service";
import { SidenavService } from "../../components/sidenav/sidenav.service";

@Component({
  selector: 'app-domain',
  templateUrl: './domain.component.html',
  styleUrls: ['./domain.component.scss']
})
export class DomainComponent implements OnInit {
  domain: any = {};

  constructor(private route: ActivatedRoute, private domainService: DomainService, private sidenavService: SidenavService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainService.notify(this.domain);
    this.sidenavService.notify(this.domain);
  }
}

