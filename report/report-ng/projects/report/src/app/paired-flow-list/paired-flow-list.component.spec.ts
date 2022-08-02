import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PairedFlowListComponent } from './paired-flow-list.component';

describe('PairedFlowListComponent', () => {
  let component: PairedFlowListComponent;
  let fixture: ComponentFixture<PairedFlowListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ PairedFlowListComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PairedFlowListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
