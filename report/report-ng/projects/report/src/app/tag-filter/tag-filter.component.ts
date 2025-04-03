import { Component, OnInit, Input, ViewChild, ElementRef } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatChipInputEvent } from '@angular/material/chips';
import { ENTER } from '@angular/cdk/keycodes';
import { FlowFilterService, Type } from '../flow-filter.service';
import { Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { IconEmbedService } from '../icon-embed.service';


@Component({
  selector: 'app-tag-filter',
  templateUrl: './tag-filter.component.html',
  styleUrls: ['./tag-filter.component.css']
})
export class TagFilterComponent implements OnInit {

  constructor(
    private filterService: FlowFilterService,
    private icons: IconEmbedService,) {
    icons.register("cancel", "close");
  }

  @Input() type: Type = Type.Include;
  @Input() tags: Set<string> = new Set();
  @Input() disabled: boolean = false;
  filters: Set<string> = new Set();
  availableTags: Set<string> = new Set();
  completionTags!: Observable<string[]>;

  ctrl = new UntypedFormControl();
  separatorKeysCodes: number[] = [ENTER];

  @ViewChild('filterInput') filterInput!: ElementRef<HTMLInputElement>;

  ngOnInit(): void {
    this.filterService.onUpdate(() => {
      this.refresh();
    });
    this.refresh();
  }

  ngOnChanges(): void {
    this.availableTags = new Set(this.tags);
    this.filterService.all().forEach(f => this.availableTags.delete(f));
    this.completionTags = this.ctrl.valueChanges.pipe(
      startWith(''),
      map(value => Array.from(this.availableTags)
        .filter(tag => (tag || '').toLowerCase().includes((value || '').toLowerCase())
        )));
  }

  refresh(): void {
    this.filters = new Set(Array.from(this.filterService.get(this.type)).sort());
  }

  /**
   * Called when a filter is added via the text input
   * @param event 
   */
  add(event: MatChipInputEvent) {
    const value = (event.value || '').trim();
    if (value) {
      this.filters.add(value);
    }
    event.chipInput!.clear();
    this.ctrl.setValue(null);
    this.filterService.set(this.type, this.filters);
    this.refresh();
  }
  /**
   * Called when a filter is removed
   * @param filter
   */
  remove(filter: string) {
    this.filters.delete(filter);
    this.filterService.set(this.type, this.filters);
    this.refresh();
  }

  /**
   * Called when the clear icon is clicked
   */
  clear(): void {
    this.filters.clear();
    this.filterService.set(this.type, this.filters);
    this.refresh();
  }

  /**
   * Called when a filter is chosen from the autocomplete menu
   * @param event 
   */
  selected(event: MatAutocompleteSelectedEvent): void {
    this.filters.add(event.option.viewValue);
    this.ctrl.setValue(null);
    this.filterInput.nativeElement.value = '';
    this.filterService.set(this.type, this.filters);
    this.refresh();
  }

  /**
   * 
   * @param event Called when a tag is dropped
   */
  drop(event: CdkDragDrop<Set<string>>) {
    const dropped: string = Array.from(event.previousContainer.data)[event.previousIndex];
    if (event.container.data.has(dropped)) {
      // no change
    }
    else {
      event.previousContainer.data.delete(dropped);
      event.container.data.add(dropped);
      this.filterService.flip(dropped);
    }
    this.refresh();
  }
}

