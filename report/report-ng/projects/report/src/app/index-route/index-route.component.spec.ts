import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IndexRouteComponent } from './index-route.component';
import { RouterModule } from '@angular/router';

describe('IndexRouteComponent', () => {
  let component: IndexRouteComponent;
  let fixture: ComponentFixture<IndexRouteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [IndexRouteComponent],
      imports: [RouterModule]
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
