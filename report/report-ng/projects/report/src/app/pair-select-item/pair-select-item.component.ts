import { Component, Input, OnInit } from '@angular/core';
import { DiffPair } from '../flow-diff.service';

export interface ListPair { index: number, pair: DiffPair };

@Component({
  selector: 'app-pair-select-item',
  templateUrl: './pair-select-item.component.html',
  styleUrls: ['./pair-select-item.component.css']
})
export class PairSelectItemComponent implements OnInit {

  @Input() showResult: boolean = true;
  @Input() item!: ListPair;
  @Input() selectedIndex: number = -1;

  description: string = '';
  tags: string[] = [];

  constructor() { }

  ngOnInit(): void {

    let dt = new Set<string>();

    if (this.item.pair.left !== null) {
      this.description = this.item.pair.left.entry.description;
      this.item.pair.left.entry.tags.forEach(t => dt.add(t));
    }

    if (this.item.pair.right !== null) {
      if (this.description !== this.item.pair.right.entry.description) {
        if (this.description.length) {
          this.description += " ↦ ";
        }
        this.description += this.item.pair.right.entry.description;
      }
      this.item.pair.right.entry.tags.forEach(t => dt.add(t));
    }

    if (!this.showResult) {
      dt.delete("PASS");
      dt.delete("FAIL");
      dt.delete("SKIP");
      dt.delete("ERROR");
    }
    this.tags = Array.from(dt.values());
    this.tags.sort();
  }
}
