import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PairSelectItemComponent } from './pair-select-item.component';

describe('PairSelectItemComponent', () => {
  let component: PairSelectItemComponent;
  let fixture: ComponentFixture<PairSelectItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ PairSelectItemComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PairSelectItemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
