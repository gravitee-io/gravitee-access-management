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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { FormControl } from "@angular/forms";
import { ClientService } from "../../../services/client.service";

@Component({
  selector: 'app-select-clients',
  templateUrl: './select-clients.component.html'
})
export class SelectClientsComponent implements OnInit {
  private domainId: string;
  clientCtrl = new FormControl();
  filteredClients: any[];
  @Input() selectedClient: any;
  @Output() onSelectClient = new EventEmitter<any>();
  @Output() onRemoveClient = new EventEmitter<any>();

  constructor(private route: ActivatedRoute,
              private clientService: ClientService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.clientCtrl.valueChanges
      .subscribe(searchTerm => {
        if (typeof(searchTerm) === 'string' || searchTerm instanceof String) {
          this.clientService.search(this.domainId, searchTerm).map(res => res.json()).subscribe(response => {
            this.filteredClients = response;
          });
        }
      });

    if (this.selectedClient) {
      this.clientCtrl.setValue({clientName: this.selectedClient.clientName, clientId: this.selectedClient.clientId});
    }
  }

  onClientSelectionChanged(event) {
    this.selectedClient = event.option.value;
    this.onSelectClient.emit(this.selectedClient);
  }

  displayFn(client?: any): string | undefined {
    return client ? (client.clientName ? client.clientName : client.clientId) : undefined;
  }

  displayClientName(client) {
    return (client.clientName) ? client.clientName : client.clientId;
  }

  removeClient(event) {
    event.preventDefault();
    this.onRemoveClient.emit(this.selectedClient);
    this.selectedClient = null;
    this.clientCtrl.setValue('');
  }
}
