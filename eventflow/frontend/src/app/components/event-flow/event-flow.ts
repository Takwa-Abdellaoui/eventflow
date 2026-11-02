import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { OrderService } from '../../services/order.service';
import { Order } from '../../models/order.model';

interface FlowEvent {
  time: string;
  orderId: string;
  event: string;
  status: string;
  color: string;
  icon: string;
}

@Component({
  selector: 'app-event-flow',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './event-flow.html',
  styleUrl: './event-flow.scss'
})
export class EventFlow implements OnInit, OnDestroy {
  events: FlowEvent[] = [];
  private sub!: Subscription;
  private seenOrders = new Map<string, string>();

  constructor(private orderService: OrderService) {}

  ngOnInit() {
    this.sub = this.orderService.pollOrders(2000).subscribe(orders => {
      orders.forEach(order => {
        const prev = this.seenOrders.get(order.id);
        if (prev !== order.status) {
          this.seenOrders.set(order.id, order.status);
          if (prev !== undefined) {
            this.addEvent(order);
          } else {
            this.addEvent(order);
          }
        }
      });
    });
  }

  addEvent(order: Order) {
    const map: any = {
      'PENDING':             { event: 'order.created',        color: '#fbbf24', icon: '📤' },
      'INVENTORY_RESERVED':  { event: 'inventory.reserved',   color: '#60a5fa', icon: '📦' },
      'CONFIRMED':           { event: 'payment.processed ✅', color: '#4ade80', icon: '✅' },
      'FAILED':              { event: 'payment.failed ❌',    color: '#f87171', icon: '❌' },
      'CANCELLED':           { event: 'order.cancelled',      color: '#78716c', icon: '🚫' },
    };

    const meta = map[order.status] || { event: order.status, color: '#64748b', icon: '•' };

    this.events.unshift({
      time: new Date().toLocaleTimeString(),
      orderId: order.id.substring(0, 8),
      event: meta.event,
      status: order.status,
      color: meta.color,
      icon: meta.icon
    });

    if (this.events.length > 20) this.events.pop();
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}