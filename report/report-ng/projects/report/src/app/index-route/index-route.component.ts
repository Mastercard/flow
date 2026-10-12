import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { IndexDataService } from '../index-data.service';
import { empty_index, Index } from '../types';

@Component({
    selector: 'app-index-route',
    templateUrl: './index-route.component.html',
    styleUrls: ['./index-route.component.css'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class IndexRouteComponent implements OnInit {

  @Input() index: Index = empty_index;

  constructor(private indexData: IndexDataService) { }

  ngOnInit(): void {
    this.indexData.set(this.index);
  }

  // looking for the route definitions? They're in app.module.ts
}
