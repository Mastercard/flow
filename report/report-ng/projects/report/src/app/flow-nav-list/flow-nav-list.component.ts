import { Component, OnInit, Input } from '@angular/core';
import { Entry } from '../types';
import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';

@Component({
  selector: 'app-flow-nav-list',
  templateUrl: './flow-nav-list.component.html',
  styleUrls: ['./flow-nav-list.component.css']
})
export class FlowNavListComponent implements OnInit {

  @Input() basePath: string = "";
  @Input() showResult: boolean = true;
  @Input() draggable: boolean = false;
  @Input() entries: Entry[] = [];

  ngOnInit(): void {
  }

  drop(event: CdkDragDrop<string[]>) {
    moveItemInArray(this.entries, event.previousIndex, event.currentIndex);
  }
}
