import { Component, OnInit, Input, AfterViewInit, ViewChild, ChangeDetectionStrategy } from '@angular/core';

interface Node {
  name: string;
  value?: string;
  children?: Node[];
}

@Component({
    selector: 'app-context-view',
    templateUrl: './context-view.component.html',
    styleUrls: ['./context-view.component.css'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})

export class ContextViewComponent implements OnInit {
  @Input() context: any = {};
  ngOnInit(): void {
  }
}
