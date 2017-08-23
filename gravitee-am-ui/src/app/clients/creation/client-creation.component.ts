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
import { Location } from '@angular/common';
import { ClientService } from "../../services/client.service";
import { ActivatedRoute, Router } from "@angular/router";
import { SnackbarService } from "../../services/snackbar.service";

@Component({
  selector: 'app-creation',
  templateUrl: './client-creation.component.html',
  styleUrls: ['./client-creation.component.scss']
})
export class ClientCreationComponent implements OnInit {
  private domainId: string;
  selectedDomainId: string;
  client: any = {};
  domains: any[];

  constructor(private clientService: ClientService, private router: Router, private route: ActivatedRoute,
              private snackbarService : SnackbarService, private location: Location) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.domains = this.route.snapshot.data['domains'];
    this.route.params.subscribe(params => {
      this.selectedDomainId = params['domain'];
    });
  }

  create() {
    this.clientService.create(this.selectedDomainId, this.client).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Client " + data.clientId + " created");
      this.router.navigate(['/domains', this.selectedDomainId, 'clients', data.id]);
    });
  }

}
