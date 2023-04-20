import { Component, Input, OnInit } from '@angular/core';
import { MatLegacySelectionListChange as MatSelectionListChange } from '@angular/material/legacy-list';
import { DiffTableFormat } from 'ngx-text-diff/lib/ngx-text-diff.model';
import { Residue, residueAsserted, residueAssertionPassed } from '../types';

@Component({
  selector: 'app-residue-view',
  templateUrl: './residue-view.component.html',
  styleUrls: ['./residue-view.component.css']
})
export class ResidueViewComponent implements OnInit {
  @Input() residues: Residue[] = [];
  diffFormat: DiffTableFormat = 'LineByLine';

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
