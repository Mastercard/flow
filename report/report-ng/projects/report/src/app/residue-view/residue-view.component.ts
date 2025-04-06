import { Component, Input, OnInit, inject } from '@angular/core';
import { Residue, residueAsserted, residueAssertionPassed } from '../types';
import { DiffDisplay } from '../text-diff/text-diff.component';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-residue-view',
  templateUrl: './residue-view.component.html',
  styleUrls: ['./residue-view.component.css']
})
export class ResidueViewComponent implements OnInit {
  private icons = inject(IconEmbedService);

  @Input() residues: Residue[] = [];
  diffFormat: DiffDisplay = 'unified';

  constructor() {
    const icons = this.icons;

    icons.register("check_circle_outline", "error_outline");
  }

  ngOnInit(): void {
  }

  assertionPassed(residue: Residue): boolean {
    return residueAsserted(residue) && residueAssertionPassed(residue);
  }

  assertionFailed(residue: Residue): boolean {
    return residueAsserted(residue) && !residueAssertionPassed(residue);
  }

}
