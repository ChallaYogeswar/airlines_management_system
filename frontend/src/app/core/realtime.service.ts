import { Injectable, OnDestroy, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable } from 'rxjs';
import { backendConfig } from './backend.config';

export type ConnectionState = 'connecting' | 'connected' | 'disconnected';

/**
 * Single STOMP-over-SockJS connection shared by every feature that needs
 * live backend data. Without this, FlightBoardService and RadarService
 * would each open their own socket to the same server for no reason -
 * fine for two features, wasteful and messy once there are five.
 *
 * Subscriptions are lazy and survive reconnects: subscribe() can be
 * called before the socket is even open, and topics are automatically
 * re-bound every time the connection comes back up.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService implements OnDestroy {
  readonly connectionState = signal<ConnectionState>('connecting');

  private readonly client: Client;
  private readonly topicHandlers = new Map<string, Set<(message: IMessage) => void>>();
  private readonly activeSubs = new Map<string, StompSubscription>();

  constructor() {
    this.client = new Client({
      webSocketFactory: () => new SockJS(backendConfig.wsUrl),
      reconnectDelay: 4000,
      onConnect: () => {
        this.connectionState.set('connected');
        for (const topic of this.topicHandlers.keys()) {
          this.bindTopic(topic);
        }
      },
      onWebSocketClose: () => {
        this.connectionState.set('disconnected');
        this.activeSubs.clear();
      },
      onStompError: () => {
        this.connectionState.set('disconnected');
      },
    });
    this.client.activate();
  }

  ngOnDestroy(): void {
    this.client.deactivate();
  }

  /** Emits parsed JSON payloads pushed to `topic`. Safe to call before the
   * socket connects - the topic is bound as soon as (and every time) the
   * connection is up. */
  subscribe<T>(topic: string): Observable<T> {
    return new Observable<T>((subscriber) => {
      const handler = (message: IMessage) => {
        try {
          subscriber.next(JSON.parse(message.body) as T);
        } catch {
          // malformed payload - drop it, don't kill the stream
        }
      };

      if (!this.topicHandlers.has(topic)) {
        this.topicHandlers.set(topic, new Set());
      }
      this.topicHandlers.get(topic)!.add(handler);

      if (this.client.connected) {
        this.bindTopic(topic);
      }

      return () => {
        this.topicHandlers.get(topic)?.delete(handler);
      };
    });
  }

  private bindTopic(topic: string): void {
    if (this.activeSubs.has(topic)) return;
    const sub = this.client.subscribe(topic, (message) => {
      this.topicHandlers.get(topic)?.forEach((handler) => handler(message));
    });
    this.activeSubs.set(topic, sub);
  }
}
