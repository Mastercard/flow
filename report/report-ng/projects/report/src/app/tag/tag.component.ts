import { Component, OnInit, Input, ChangeDetectionStrategy } from '@angular/core';
import { FlowFilterService } from '../flow-filter.service';

@Component({
    selector: 'app-tag',
    templateUrl: './tag.component.html',
    styleUrls: ['./tag.component.css'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class TagComponent implements OnInit {

  @Input() tag: string = "";

  constructor(
    public filterService: FlowFilterService) {
  }

  ngOnInit(): void {
  }

  unFiltered(): boolean {
    return !this.filterService.all().has(this.tag);
  }

  clicked(event: MouseEvent): void {
    this.filterService.addInclude(this.tag);
    if (!this.filterService.isLocked()) {
      event.stopPropagation();
    }
  }

}
