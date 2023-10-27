import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { DuctService } from './duct.service';

declare var data: any;

describe('AppComponent', () => {
  let mockDuctService;
  beforeEach(async () => {
    mockDuctService = jasmine.createSpyObj(['startHeartbeat']);
    await TestBed.configureTestingModule({
      declarations: [
        AppComponent
      ],
      providers: [
        { provide: DuctService, useValue: mockDuctService },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should reject bad data', () => {
    data = "bad data";
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled?.textContent)
      .toContain('Failed to grok data as index or flow "bad data"');
  });
});
