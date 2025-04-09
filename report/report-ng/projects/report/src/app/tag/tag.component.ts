import { Component, OnInit, Input, inject } from '@angular/core';
import { FlowFilterService } from '../flow-filter.service';

@Component({
  selector: 'app-tag',
  templateUrl: './tag.component.html',
  styleUrls: ['./tag.component.css']
})
export class TagComponent implements OnInit {
  filterService = inject(FlowFilterService);


  @Input() tag: string = "";

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
