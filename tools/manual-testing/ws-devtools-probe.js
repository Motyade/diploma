window.wsProbe = {
  events: [],
  client: null,
  subscriptions: [],

  start(storeId, deptId, sessionToken, hostRoot = window.location.origin) {
    if (typeof SockJS === "undefined" || typeof StompJs === "undefined") {
      console.error("SockJS/StompJs не найдены. Открой страницу web-app, где подключены библиотеки.");
      return;
    }

    this.stop();
    this.events = [];

    this.client = new StompJs.Client({
      webSocketFactory: () => new SockJS(hostRoot + "/ws"),
      reconnectDelay: 0,
      onConnect: () => {
        this.subscriptions.push(
          this.client.subscribe(`/topic/store/${storeId}/requests`, (msg) => this._onMsg("store", msg))
        );
        this.subscriptions.push(
          this.client.subscribe(`/topic/department/${deptId}/requests`, (msg) => this._onMsg("department", msg))
        );
        this.subscriptions.push(
          this.client.subscribe(`/queue/request/${sessionToken}`, (msg) => this._onMsg("client", msg))
        );
        console.log("WS connected and subscribed");
      },
      onStompError: (frame) => console.error("STOMP error:", frame),
      onWebSocketError: (event) => console.error("WebSocket error:", event),
      onDisconnect: () => console.log("WS disconnected"),
    });

    this.client.activate();
  },

  stop() {
    if (this.subscriptions.length) {
      this.subscriptions.forEach((s) => {
        try { s.unsubscribe(); } catch (e) {}
      });
      this.subscriptions = [];
    }

    if (this.client) {
      try { this.client.deactivate(); } catch (e) {}
      this.client = null;
    }
  },

  _onMsg(channel, msg) {
    let body = null;
    try {
      body = JSON.parse(msg.body);
    } catch (e) {
      body = { raw: msg.body };
    }

    const item = {
      at: new Date().toISOString(),
      channel,
      body,
      event_type: body?.event_type ?? body?.eventType ?? null,
      status: body?.status ?? null,
      request_id: body?.request_id ?? body?.requestId ?? null,
    };

    this.events.push(item);
    console.log(`[${channel}]`, item.event_type, item.status, item.request_id, body);
  },

  byRequest(requestId) {
    return this.events.filter((e) => e.request_id === requestId);
  },

  byEvent(eventType) {
    return this.events.filter((e) => e.event_type === eventType);
  },

  table() {
    const rows = this.events.map((e) => ({
      at: e.at,
      channel: e.channel,
      event_type: e.event_type,
      status: e.status,
      request_id: e.request_id,
    }));
    console.table(rows);
    return rows;
  },
};

console.log("wsProbe loaded. Example:");
console.log('wsProbe.start("11111111-1111-1111-1111-111111111111","22222222-2222-2222-2222-222222222222","SESSION_TOKEN","http://83.147.255.205")');
