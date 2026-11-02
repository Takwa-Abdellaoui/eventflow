import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { OrderService } from '../../services/order.service';

@Component({
  selector: 'app-stats',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stats.html',
  styleUrl: './stats.scss'
})
export class Stats implements OnInit, OnDestroy {
  stats = { total: 0, pending: 0, reserved: 0, confirmed: 0, failed: 0 };
  private sub!: Subscription;

  constructor(private orderService: OrderService) {}

  ngOnInit() {
    this.sub = this.orderService.pollOrders(3000).subscribe(orders => {
      this.stats = {
        total: orders.length,
        pending: orders.filter(o => o.status === 'PENDING').length,
        reserved: orders.filter(o => o.status === 'INVENTORY_RESERVED').length,
        confirmed: orders.filter(o => o.status === 'CONFIRMED').length,
        failed: orders.filter(o => o.status === 'FAILED').length
      };
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}