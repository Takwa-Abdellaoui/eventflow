import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { OrderService } from '../../services/order.service';
import { Order } from '../../models/order.model';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './order-list.html',
  styleUrl: './order-list.scss'
})
export class OrderList implements OnInit, OnDestroy {
  orders: Order[] = [];
  private sub!: Subscription;

  constructor(private orderService: OrderService) {}

  ngOnInit() {
    this.sub = this.orderService.pollOrders(2000).subscribe(orders => {
      this.orders = orders.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  statusLabel(status: string): string {
    const labels: any = {
      'PENDING': '⏳ En attente',
      'INVENTORY_RESERVED': '📦 Stock réservé',
      'CONFIRMED': '✅ Confirmée',
      'FAILED': '❌ Échouée',
      'CANCELLED': '🚫 Annulée'
    };
    return labels[status] || status;
  }

  statusClass(status: string): string {
    return status.toLowerCase().replace('_', '-');
  }
}