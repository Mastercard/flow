import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PairSelectItemComponent } from './pair-select-item.component';
import { empty_flow } from '../types';

describe('PairSelectItemComponent', () => {
  let component: PairSelectItemComponent;
  let fixture: ComponentFixture<PairSelectItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [PairSelectItemComponent]
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
