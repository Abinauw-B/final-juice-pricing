import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div>
      <h1 style="font-size: 26px; font-weight: 700; margin-bottom: 8px;">Executive Dashboard</h1>
      <p style="color: var(--text-muted); margin-bottom: 24px;">Real-time inventory volume, POS sales metrics, and dynamic pricing activity.</p>

      <div class="metric-grid">
        <div class="metric-card">
          <div class="metric-label">Today's Revenue</div>
          <div class="metric-val" style="color: #10b981;">₹{{ todayRevenue }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Cups Sold Today</div>
          <div class="metric-val" style="color: #3b82f6;">{{ todayCupsSold }} cups</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Active 20L Containers</div>
          <div class="metric-val" style="color: #f59e0b;">{{ activeBatchesCount }} active</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total Liquid Volume</div>
          <div class="metric-val" style="color: #8b5cf6;">{{ totalRemainingLiters }} Litres</div>
        </div>
      </div>

      <div class="section-card">
        <h2 style="font-size: 18px; font-weight: 600; margin-bottom: 16px;">Active Juice Container Batches Monitor</h2>
        <table class="table-custom">
          <thead>
            <tr>
              <th>Batch Code</th>
              <th>Flavour</th>
              <th>Container Cap (ML)</th>
              <th>Remaining Vol (ML)</th>
              <th>Estimated Cups</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let b of batches">
              <td style="font-weight: 600;">{{ b.batchCode }}</td>
              <td>{{ getFlavourName(b.productId) }}</td>
              <td>{{ b.containerCapacityMl }} ml (20L)</td>
              <td style="font-weight: 600; color: #3b82f6;">{{ b.remainingVolumeMl }} ml</td>
              <td style="font-weight: 700; color: #10b981;">{{ getEstimatedCups(b) }} cups</td>
              <td>
                <span class="status-tag" [ngClass]="b.status === 'ACTIVE' ? 'tag-active' : 'tag-depleted'">
                  {{ b.status }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class DashboardComponent implements OnInit {
  todayRevenue: number = 3840;
  todayCupsSold: number = 192;
  activeBatchesCount: number = 7;
  totalRemainingLiters: number = 118.5;

  batches: any[] = [
    { id: 1, productId: 1, batchCode: 'BATCH-MNG-001', containerCapacityMl: 20000, remainingVolumeMl: 17500, status: 'ACTIVE' },
    { id: 2, productId: 2, batchCode: 'BATCH-LMN-001', containerCapacityMl: 20000, remainingVolumeMl: 16000, status: 'ACTIVE' },
    { id: 3, productId: 3, batchCode: 'BATCH-MNT-001', containerCapacityMl: 20000, remainingVolumeMl: 19000, status: 'ACTIVE' },
    { id: 4, productId: 4, batchCode: 'BATCH-ORG-001', containerCapacityMl: 20000, remainingVolumeMl: 14250, status: 'ACTIVE' },
    { id: 5, productId: 5, batchCode: 'BATCH-STR-001', containerCapacityMl: 20000, remainingVolumeMl: 18500, status: 'ACTIVE' },
    { id: 6, productId: 6, batchCode: 'BATCH-GRP-001', containerCapacityMl: 20000, remainingVolumeMl: 15750, status: 'ACTIVE' },
    { id: 7, productId: 7, batchCode: 'BATCH-LYC-001', containerCapacityMl: 20000, remainingVolumeMl: 17500, status: 'ACTIVE' }
  ];

  flavoursMap: { [key: number]: string } = {
    1: 'Fresh Mango',
    2: 'Zesty Lemon',
    3: 'Cool Mint',
    4: 'Orange Sunrise',
    5: 'Strawberry Delight',
    6: 'Royal Grape',
    7: 'Lychee Mist'
  };

  apiBaseUrl: string = (typeof window !== 'undefined' && (window as any).API_BASE_URL) || 'http://localhost:8088/api';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<any[]>(`${this.apiBaseUrl}/batches/active`).subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.batches = data;
          this.activeBatchesCount = data.length;
          const totalMl = data.reduce((s, b) => s + b.remainingVolumeMl, 0);
          this.totalRemainingLiters = Math.round((totalMl / 1000) * 10) / 10;
        }
      },
      error: () => console.log('Using default mock batch metrics')
    });
  }

  getFlavourName(productId: number): string {
    return this.flavoursMap[productId] || 'Flavour #' + productId;
  }

  getEstimatedCups(b: any): number {
    return Math.floor(b.remainingVolumeMl / 250);
  }
}
