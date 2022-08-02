import { Component, Input, OnInit } from '@angular/core';
import { MatButtonToggleChange } from '@angular/material/button-toggle';
import { FlowFilterService, Type } from '../flow-filter.service';

@Component({
  selector: 'app-flow-filter',
  templateUrl: './flow-filter.component.html',
  styleUrls: ['./flow-filter.component.css']
})
export class FlowFilterComponent implements OnInit {

  readonly includeType: Type = Type.Include;
  readonly excludeType: Type = Type.Exclude;
  descFilter: string = "";
  includeFilters: string[] = [];
  excludeFilters: string[] = [];

  /**
   * The set of tags on all visible flows. Used to narrow auto-complete suggestions
   */
  @Input() tags: Set<string> = new Set();

  constructor(
    public filterService: FlowFilterService) {
    filterService.onUpdate(() => this.refresh());
  }

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.descFilter = this.filterService.getDescriptionMatch();
    this.includeFilters = Array.from(this.filterService.get(Type.Include));
    this.excludeFilters = Array.from(this.filterService.get(Type.Exclude));
    this.includeFilters.sort();
    this.excludeFilters.sort();
  }

  lock(event: MatButtonToggleChange): void {
    this.filterService.lock(event.source.checked);
  }

  clearFilters(event: MatButtonToggleChange): void {
    this.filterService.clear();
    // we're abusing a toggle button for the visuals, so keep it unchecked
    event.source.checked = false;
  }

  descFilterUpdated(): void {
    this.filterService.setDescriptionMatch(this.descFilter);
  }

}
