import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { ListPair, PairSelectItemComponent } from './pair-select-item.component';
import { empty_flow } from '../types';
import { MatLegacyListModule as MatListModule } from '@angular/material/legacy-list';

describe('PairSelectItemComponent', () => {
  let component: TestWrapper;
  let fixture: ComponentFixture<TestWrapper>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        PairSelectItemComponent,
        TestWrapper
      ],
      imports: [
        MatListModule
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TestWrapper);
    component = fixture.componentInstance;
    component.item = {
      index: 0,
      pair: {
        left: {
          entry: { description: "", tags: [], detail: "" },
          flow: empty_flow,
          flat: ""
        },
        right: {
          entry: { description: "", tags: [], detail: "" },
          flow: empty_flow,
          flat: ""
        },
      }
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

/**
 * It seems impossible to test a mat-list-option in isolation - it
 * has to be embedded in a mat-selection-list to avoid injector
 * errors. Hence we're going to test _this_ rather than app-pair-select-item
 * directly
 */
@Component({
  selector: 'app-flow-nav-list',
  template: `
  <mat-selection-list>
    <app-pair-select-item 
      [showResult]="false"
      [item]="item"
      [selectedIndex]="selected?.index ?? -1" >
    </app-pair-select-item>
  </mat-selection-list>`
})
class TestWrapper {
  @Input() item!: ListPair;
  @Input() selectedIndex: number = -1;
}