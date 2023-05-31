import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PairSelectItemComponent } from './pair-select-item.component';
import { empty_flow } from '../types';

describe('PairSelectItemComponent', () => {
  let component: PairSelectItemComponent;
  let fixture: ComponentFixture<PairSelectItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        PairSelectItemComponent,
      ],
      imports: [
        // We should be ok to import MatListModule in order to
        // avoid the errors about missing mat-list-option
        // component, but it provokes an error instead: 
        // NullInjectorError: No provider for MatSelectionList
        // I've got no idea what to do about that, so we'll
        // live with the non-fatal errors for now
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PairSelectItemComponent);
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
