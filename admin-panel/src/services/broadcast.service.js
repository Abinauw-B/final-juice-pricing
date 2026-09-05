// Cross-Panel BroadcastChannel Synchronization Service

import { BROADCAST_CHANNEL_NAME } from '../constants/app.constants.js';

class BroadcastService {
  constructor() {
    this.channel = new BroadcastChannel(BROADCAST_CHANNEL_NAME);
    this.listeners = [];

    this.channel.onmessage = (event) => {
      this.listeners.forEach(fn => fn(event.data));
    };

    window.addEventListener('storage', (e) => {
      if (e.key === 'pubexchange_event' && e.newValue) {
        try {
          const data = JSON.parse(e.newValue);
          this.listeners.forEach(fn => fn(data));
        } catch (err) {}
      }
    });
  }

  subscribe(callback) {
    this.listeners.push(callback);
    return () => {
      this.listeners = this.listeners.filter(fn => fn !== callback);
    };
  }

  broadcast(type, payload = {}) {
    const msg = { type, payload, timestamp: Date.now() };
    try {
      this.channel.postMessage(msg);
      localStorage.setItem('pubexchange_event', JSON.stringify(msg));

      const bridgeIframe = document.getElementById('posBridgeFrame');
      if (bridgeIframe && bridgeIframe.contentWindow) {
        const targetOrigin = (typeof window !== 'undefined' && window.CONFIG && window.CONFIG.POS_URL) ? window.CONFIG.POS_URL : '*';
        bridgeIframe.contentWindow.postMessage(msg, targetOrigin);
      }
    } catch (e) {}
  }
}

export const broadcastService = new BroadcastService();
