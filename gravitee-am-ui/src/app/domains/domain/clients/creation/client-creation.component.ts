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
import { Component, OnInit, OnDestroy } from '@angular/core';
import { ClientService } from "../../../../services/client.service";
import { Router } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";
import { Subscription } from "rxjs";
import { DomainService } from "../../../../services/domain.service";

@Component({
  selector: 'app-creation',
  templateUrl: 'client-creation.component.html',
  styleUrls: ['client-creation.component.scss']
})
export class ClientCreationComponent implements OnInit, OnDestroy {
  private domain: any;
  private subscription: Subscription;
  client: any = {};

  constructor(private clientService: ClientService, private router: Router,
              private snackbarService : SnackbarService, private domainService: DomainService) { }

  ngOnInit() {
    this.subscription = this.domainService.notifyObservable$.subscribe(data => {
      this.domain = data;
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  create() {
    this.clientService.create(this.domain.id, this.client).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Client " + data.clientId + " created");
      this.router.navigate(['/domains', this.domain.id, 'clients', data.id, 'edit']);
    });
  }

}
