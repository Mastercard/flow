import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UnpairedFlowListComponent } from './unpaired-flow-list.component';

describe('UnpairedFlowListComponent', () => {
  let component: UnpairedFlowListComponent;
  let fixture: ComponentFixture<UnpairedFlowListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ UnpairedFlowListComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(UnpairedFlowListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
