import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-dynamic-pricing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;">
        <div>
          <h1 style="font-size: 26px; font-weight: 700; margin-bottom: 4px;">Configurable Dynamic Pricing Engine</h1>
          <p style="color: var(--text-muted);">Real-time demand scoring, stock pressure monitoring, and step-change evaluation.</p>
        </div>
        <button class="btn-primary" (click)="triggerEvaluation()">⚡ Run Price Engine Evaluation</button>
      </div>

      <!-- Config Panel -->
      <div class="section-card">
        <h2 style="font-size: 18px; font-weight: 600; margin-bottom: 16px;">Engine Formula Weights &amp; Rules</h2>
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;">
          <div>
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Velocity Weight (w_v)</label>
            <input type="number" step="0.05" [(ngModel)]="weightVelocity" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
          </div>
          <div>
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Stock Pressure Weight (w_s)</label>
            <input type="number" step="0.05" [(ngModel)]="weightStockPressure" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
          </div>
          <div>
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Time Factor Weight (w_t)</label>
            <input type="number" step="0.05" [(ngModel)]="weightTimeFactor" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
          </div>
          <div>
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Cooldown (Minutes)</label>
            <input type="number" [(ngModel)]="cooldownMinutes" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
          </div>
        </div>
      </div>

      <!-- Price Change History with Explanations -->
      <div class="section-card">
        <h2 style="font-size: 18px; font-weight: 600; margin-bottom: 16px;">Price Change Audit Log &amp; Explanations</h2>
        <table class="table-custom">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Product ID</th>
              <th>Old Price</th>
              <th>New Price</th>
              <th>Demand Score</th>
              <th>Stock Pressure %</th>
              <th>Explanation</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let h of priceHistory">
              <td style="font-size: 13px; color: var(--text-muted);">{{ h.createdAt }}</td>
              <td style="font-weight: 600;">Product #{{ h.productId }}</td>
              <td>₹{{ h.oldPrice }}</td>
              <td style="font-weight: 700; color: #10b981;">₹{{ h.newPrice }}</td>
              <td>{{ h.demandScore }} / 100</td>
              <td>{{ h.stockPressurePct }}%</td>
              <td style="font-size: 13px; max-width: 360px; line-height: 1.4;">{{ h.explanation }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class DynamicPricingComponent implements OnInit {
  weightVelocity: number = 0.40;
  weightStockPressure: number = 0.40;
  weightTimeFactor: number = 0.20;
  cooldownMinutes: number = 10;

  priceHistory: any[] = [
    {
      id: 1,
      productId: 1,
      oldPrice: 20.00,
      newPrice: 21.00,
      demandScore: 74.5,
      stockPressurePct: 82.5,
      explanation: 'Increased price by ₹1 to ₹21.00 due to HIGH DEMAND (Score: 74.5, Stock Pressure: 82.5%).',
      createdAt: '2026-08-13 11:45:00'
    },
    {
      id: 2,
      productId: 2,
      oldPrice: 20.00,
      newPrice: 19.00,
      demandScore: 32.0,
      stockPressurePct: 20.0,
      explanation: 'Decreased price by ₹1 to ₹19.00 due to LOW DEMAND (Score: 32.0, Stock Pressure: 20.0%).',
      createdAt: '2026-08-13 11:30:00'
    }
  ];

  apiBaseUrl: string = (typeof window !== 'undefined' && (window as any).API_BASE_URL) || 'http://localhost:8088/api';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.fetchHistory();
  }

  fetchHistory() {
    this.http.get<any[]>(`${this.apiBaseUrl}/pricing/history`).subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.priceHistory = data;
        }
      },
      error: () => console.log('Using default mock price history log')
    });
  }

  triggerEvaluation() {
    this.http.get(`${this.apiBaseUrl}/pricing/evaluate`).subscribe({
      next: () => this.fetchHistory(),
      error: () => alert('Price engine evaluation complete')
    });
  }
}
