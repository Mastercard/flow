import { Component, Input, OnInit, inject } from '@angular/core';
import { IndexDataService } from '../index-data.service';
import { empty_index, Index } from '../types';

@Component({
  selector: 'app-index-route',
  templateUrl: './index-route.component.html',
  styleUrls: ['./index-route.component.css']
})
export class IndexRouteComponent implements OnInit {
  private indexData = inject(IndexDataService);


  @Input() index: Index = empty_index;

  ngOnInit(): void {
    this.indexData.set(this.index);
  }

  // looking for the route definitions? They're in app.module.ts
}
