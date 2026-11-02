import { Component } from '@angular/core';
import { OrderForm } from './components/order-form/order-form';
import { OrderList } from './components/order-list/order-list';
import { EventFlow } from './components/event-flow/event-flow';
import { Stats } from './components/stats/stats';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [OrderForm, OrderList, EventFlow, Stats],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  onOrderCreated() {}
}