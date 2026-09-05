package com.retailpos.websocket;

import com.retailpos.pricing.MarketCrashService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@SuppressWarnings("null")
public class WebSocketGatewayController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketGatewayController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketCrashService marketCrashService;

    public WebSocketGatewayController(SimpMessagingTemplate messagingTemplate, MarketCrashService marketCrashService) {
        this.messagingTemplate = messagingTemplate;
        this.marketCrashService = marketCrashService;
    }

    public static class STOMPHeartbeatMessage {
        private String status;
        private String serverTimestamp;
        private boolean crashActive;

        public STOMPHeartbeatMessage() {}
        public STOMPHeartbeatMessage(String status, String serverTimestamp, boolean crashActive) {
            this.status = status;
            this.serverTimestamp = serverTimestamp;
            this.crashActive = crashActive;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getServerTimestamp() { return serverTimestamp; }
        public void setServerTimestamp(String serverTimestamp) { this.serverTimestamp = serverTimestamp; }
        public boolean isCrashActive() { return crashActive; }
        public void setCrashActive(boolean crashActive) { this.crashActive = crashActive; }

        public static STOMPHeartbeatMessageBuilder builder() { return new STOMPHeartbeatMessageBuilder(); }
        public static class STOMPHeartbeatMessageBuilder {
            private String status;
            private String serverTimestamp;
            private boolean crashActive;

            public STOMPHeartbeatMessageBuilder status(String status) { this.status = status; return this; }
            public STOMPHeartbeatMessageBuilder serverTimestamp(String serverTimestamp) { this.serverTimestamp = serverTimestamp; return this; }
            public STOMPHeartbeatMessageBuilder crashActive(boolean crashActive) { this.crashActive = crashActive; return this; }
            public STOMPHeartbeatMessage build() { return new STOMPHeartbeatMessage(status, serverTimestamp, crashActive); }
        }
    }

    @MessageMapping("/ping")
    @SendTo("/topic/status")
    public STOMPHeartbeatMessage handlePing() {
        return STOMPHeartbeatMessage.builder()
                .status("ONLINE")
                .serverTimestamp(LocalDateTime.now().toString())
                .crashActive(marketCrashService.isCrashActive())
                .build();
    }

    public void broadcastMarketCrashAlert(MarketCrashService.MarketCrashStatus crashStatus) {
        log.info("📢 Broadcasting Market Crash Alert via WebSocket: {}", crashStatus);
        messagingTemplate.convertAndSend("/topic/market-crash", crashStatus);
    }

    public void broadcastLEDDisplayPayload(Object payload) {
        messagingTemplate.convertAndSend("/topic/led-display", payload);
    }
}
