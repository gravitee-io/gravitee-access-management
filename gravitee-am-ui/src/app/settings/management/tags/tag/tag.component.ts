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
import {NgForm} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../services/snackbar.service';
import {TagService} from '../../../../services/tag.service';
import {DialogService} from '../../../../services/dialog.service';
import {AuthService} from '../../../../services/auth.service';

@Component({
  selector: 'app-tag',
  templateUrl: './tag.component.html',
  styleUrls: ['./tag.component.scss']
})
export class TagComponent implements OnInit {
  tag: any;
  @ViewChild('tagForm') public tagForm: NgForm;
  readonly: boolean;

  constructor(private tagService: TagService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.tag = this.route.snapshot.data['tag'];
    this.readonly = !this.authService.hasPermissions(['organization_tag_update']);
  }

  update() {
    this.tagService.update(this.tag.id, this.tag).subscribe(data => {
      this.tag = data;
      this.tagForm.reset(Object.assign({}, this.tag));
      this.snackbarService.open('Sharding tag updated');
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Sharding Tag', 'Are you sure you want to delete this sharding tag ?')
      .subscribe(res => {
        if (res) {
          this.tagService.delete(this.tag.id).subscribe(response => {
            this.snackbarService.open('Sharding tag deleted');
            this.router.navigate(['/settings', 'tags']);
          });
        }
      });
  }
}
