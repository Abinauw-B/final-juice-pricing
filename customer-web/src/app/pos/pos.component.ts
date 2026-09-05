import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { OrderConfirmationComponent } from './order-confirmation.component';

export interface Product {
  id: number;
  name: string;
  flavour: string;
  description: string;
  defaultCupSizeMl: number;
  currentCupPrice: number;
  minCupPrice: number;
  maxCupPrice: number;
}

export interface CartItem {
  product: Product;
  quantity: number;
  cupSizeMl: number;
}

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [CommonModule, FormsModule, OrderConfirmationComponent],
  template: `
    <div class="pos-container">
      <header class="header-banner">
        <div class="brand-title">
          <div class="brand-icon">🍹</div>
          <div>
            <div class="brand-name">Juice Shop POS</div>
            <div style="font-size: 13px; color: var(--text-muted);">Fresh Cold-Pressed Serving Terminal &bull; 250ml Cup Standard</div>
          </div>
        </div>
        <div style="display: flex; gap: 12px; align-items: center;">
          <span style="font-size: 13px; padding: 6px 12px; background: rgba(16,185,129,0.15); color: #10b981; border-radius: 20px; border: 1px solid rgba(16,185,129,0.3);">
            &bull; POS Live @ Port 8000
          </span>
        </div>
      </header>

      <!-- Flavours Grid -->
      <main>
        <h2 style="margin-bottom: 16px; font-size: 20px; font-weight: 600;">Available Fresh Juice Flavours</h2>
        <div class="flavour-grid">
          <div *ngFor="let p of products" class="flavour-card">
            <span class="flavour-badge" [ngClass]="getDemandBadgeClass(p)">
              {{ getDemandBadgeText(p) }}
            </span>
            <div class="flavour-name">{{ p.name }}</div>
            <div class="flavour-desc">{{ p.description }}</div>
            
            <div class="price-row">
              <div>
                <span class="price-tag">₹{{ p.currentCupPrice }}</span>
                <span class="cup-label"> / {{ p.defaultCupSizeMl }}ml cup</span>
              </div>
              <span style="font-size: 11px; color: var(--text-muted);">[Range: ₹{{p.minCupPrice}}-₹{{p.maxCupPrice}}]</span>
            </div>

            <button class="add-btn" (click)="addToCart(p)">+ Add Cup to Cart</button>
          </div>
        </div>
      </main>

      <!-- Cart Summary Panel -->
      <aside class="cart-panel">
        <div class="cart-title">
          <span>Current Order Cart</span>
          <span style="font-size: 14px; color: var(--text-muted);">{{ cart.length }} items</span>
        </div>

        <div class="cart-items">
          <div *ngIf="cart.length === 0" style="text-align: center; color: var(--text-muted); padding: 40px 0;">
            🛒 Cart is empty.<br>Click "+ Add Cup" to select juice.
          </div>

          <div *ngFor="let item of cart; let i = index" class="cart-item">
            <div>
              <div style="font-weight: 600;">{{ item.product.name }}</div>
              <div style="font-size: 12px; color: var(--text-muted);">
                {{ item.cupSizeMl }}ml &bull; ₹{{ item.product.currentCupPrice }} / cup &bull; Vol: {{ item.cupSizeMl * item.quantity }}ml
              </div>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <button class="item-qty-btn" (click)="updateQuantity(i, -1)">-</button>
              <span style="font-weight: 600; width: 20px; text-align: center;">{{ item.quantity }}</span>
              <button class="item-qty-btn" (click)="updateQuantity(i, 1)">+</button>
            </div>
          </div>
        </div>

        <div class="cart-summary" *ngIf="cart.length > 0">
          <div class="summary-row">
            <span>Total Volume</span>
            <span>{{ getTotalVolumeMl() }} ml ({{ getTotalVolumeMl() / 250 }} cups)</span>
          </div>
          <div class="summary-row">
            <span>Payment Method</span>
            <select [(ngModel)]="paymentMethod" style="background: #0f172a; color: white; border: 1px solid var(--border-color); padding: 4px 8px; border-radius: 6px;">
              <option value="CASH">Cash Payment</option>
              <option value="UPI">UPI / QR Code</option>
              <option value="CARD">Debit / Credit Card</option>
            </select>
          </div>
          <div class="summary-row summary-total">
            <span>Total Payable</span>
            <span style="color: #10b981;">₹{{ getTotalAmount() }}</span>
          </div>
        </div>

        <button class="checkout-btn" [disabled]="cart.length === 0" (click)="processCheckout()">
          ⚡ Complete POS Checkout (₹{{ getTotalAmount() }})
        </button>
      </aside>

      <!-- Order Confirmation Receipt Modal -->
      <app-order-confirmation 
        *ngIf="completedOrder" 
        [order]="completedOrder" 
        (close)="completedOrder = null">
      </app-order-confirmation>
    </div>
  `
})
export class PosComponent implements OnInit {
  products: Product[] = [
    { id: 1, name: 'Fresh Mango Juice', flavour: 'MANGO', description: 'Sweet fresh Alphanso mango pulp juice', defaultCupSizeMl: 250, currentCupPrice: 20, minCupPrice: 18, maxCupPrice: 25 },
    { id: 2, name: 'Zesty Lemon Juice', flavour: 'LEMON', description: 'Refreshing squeezed lemonade with mint touch', defaultCupSizeMl: 250, currentCupPrice: 20, minCupPrice: 18, maxCupPrice: 25 },
    { id: 3, name: 'Cool Mint Cooler', flavour: 'MINT', description: 'Chilled mint and lime mocktail blend', defaultCupSizeMl: 250, currentCupPrice: 20, minCupPrice: 18, maxCupPrice: 25 },
    { id: 4, name: 'Orange Sunrise', flavour: 'ORANGE', description: 'Pure Valencia orange juice loaded with vitamin C', defaultCupSizeMl: 250, currentCupPrice: 20, minCupPrice: 18, maxCupPrice: 25 },
    { id: 5, name: 'Strawberry Delight', flavour: 'STRAWBERRY', description: 'Fresh strawberry nectar crush', defaultCupSizeMl: 250, currentCupPrice: 20, minCupPrice: 18, maxCupPrice: 25 },
    { id: 6, name: 'Royal Grape Juice', flavour: 'GRAPE', description: 'Rich black grape extract cooler', defaultCupSizeMl: 250, currentCupPrice: 20, minCupPrice: 18, maxCupPrice: 25 },
    { id: 7, name: 'Lychee Mist', flavour: 'LYCHEE', description: 'Exotic lychee fruit punch', defaultCupSizeMl: 250, currentCupPrice: 20, minCupPrice: 18, maxCupPrice: 25 }
  ];

  cart: CartItem[] = [];
  paymentMethod: string = 'CASH';
  completedOrder: any = null;

  apiBaseUrl: string = (typeof window !== 'undefined' && (window as any).API_BASE_URL) || 'http://localhost:8088/api';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.fetchProducts();
  }

  fetchProducts() {
    this.http.get<Product[]>(`${this.apiBaseUrl}/pos/products`).subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.products = data;
        }
      },
      error: () => {
        console.log('Using default mock seed products for POS terminal');
      }
    });
  }

  addToCart(product: Product) {
    const existing = this.cart.find(item => item.product.id === product.id);
    if (existing) {
      existing.quantity += 1;
    } else {
      this.cart.push({ product, quantity: 1, cupSizeMl: product.defaultCupSizeMl });
    }
  }

  updateQuantity(index: number, change: number) {
    this.cart[index].quantity += change;
    if (this.cart[index].quantity <= 0) {
      this.cart.splice(index, 1);
    }
  }

  getTotalVolumeMl(): number {
    return this.cart.reduce((sum, item) => sum + (item.cupSizeMl * item.quantity), 0);
  }

  getTotalAmount(): number {
    return this.cart.reduce((sum, item) => sum + (item.product.currentCupPrice * item.quantity), 0);
  }

  getDemandBadgeClass(product: Product): string {
    if (product.currentCupPrice >= 22) return 'badge-high';
    if (product.currentCupPrice <= 19) return 'badge-low';
    return 'badge-normal';
  }

  getDemandBadgeText(product: Product): string {
    if (product.currentCupPrice >= 22) return '🔥 High Demand';
    if (product.currentCupPrice <= 19) return '⚡ Discounted Rate';
    return '✨ Standard Price';
  }

  processCheckout() {
    if (this.cart.length === 0) return;

    const payload = {
      items: this.cart.map(item => ({
        productId: item.product.id,
        quantity: item.quantity,
        cupSizeMl: item.cupSizeMl
      })),
      paymentMethod: this.paymentMethod
    };

    this.http.post(`${this.apiBaseUrl}/pos/checkout`, payload).subscribe({
      next: (res: any) => {
        this.completedOrder = res;
        this.cart = [];
        this.fetchProducts(); // Refresh dynamic prices
      },
      error: (err) => {
        // Fallback local receipt demo if backend is offline
        const orderNum = 'ORD-' + Math.floor(100000 + Math.random() * 900000);
        this.completedOrder = {
          orderNumber: orderNum,
          totalAmount: this.getTotalAmount(),
          paymentMethod: this.paymentMethod,
          paymentStatus: 'COMPLETED',
          timestamp: new Date().toISOString(),
          items: this.cart.map(i => ({
            productName: i.product.name,
            quantity: i.quantity,
            cupSizeMl: i.cupSizeMl,
            unitPrice: i.product.currentCupPrice,
            totalPrice: i.product.currentCupPrice * i.quantity,
            volumeDeductedMl: i.cupSizeMl * i.quantity
          }))
        };
        this.cart = [];
      }
    });
  }
}
