import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IndexRouteComponent } from './index-route.component';

describe('IndexRouteComponent', () => {
  let component: IndexRouteComponent;
  let fixture: ComponentFixture<IndexRouteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ IndexRouteComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(IndexRouteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
