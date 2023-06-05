import { Component, Input, OnInit } from '@angular/core';
import { Residue, residueAsserted, residueAssertionPassed } from '../types';
import { DiffDisplay } from '../text-diff/text-diff.component';

@Component({
  selector: 'app-residue-view',
  templateUrl: './residue-view.component.html',
  styleUrls: ['./residue-view.component.css']
})
export class ResidueViewComponent implements OnInit {
  @Input() residues: Residue[] = [];
  diffFormat: DiffDisplay = 'unified';

  constructor() { }

  ngOnInit(): void {
  }

  assertionPassed(residue: Residue): boolean {
    return residueAsserted(residue) && residueAssertionPassed(residue);
  }

  assertionFailed(residue: Residue): boolean {
    return residueAsserted(residue) && !residueAssertionPassed(residue);
  }

}
