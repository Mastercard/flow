import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ContextViewComponent } from './context-view.component';

describe('ContextViewComponent', () => {
  let component: ContextViewComponent;
  let fixture: ComponentFixture<ContextViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ContextViewComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ContextViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
