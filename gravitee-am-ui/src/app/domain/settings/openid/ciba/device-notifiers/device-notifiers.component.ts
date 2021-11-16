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
import {Component, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';

@Component({
  selector: 'app-device-notifiers',
  templateUrl: './device-notifiers.component.html',
  styleUrls: ['./device-notifiers.component.scss']
})
export class DeviceNotifiersComponent implements OnInit {
  private notifierTypes: any = {
    'http-am-authdevice-notifier' : 'External HTTP service'
  };

  notifiers: any[];
  domainId: any;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.notifiers = this.route.snapshot.data['notifiers'];
    console.debug("DeviceNotifiersComponent initialized with notifiers", this.notifiers);
  }

  isEmpty() {
    return !this.notifiers || this.notifiers.length === 0;
  }

  displayType(type) {
    if (this.notifierTypes[type]) {
      return this.notifierTypes[type];
    }
    return type;
  }
}
