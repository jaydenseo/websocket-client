package com.example.ccbe.websocket;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.JsonbMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.example.ccbe.websocket.model.ChatMessage;
import com.example.ccbe.websocket.model.ChatMessage.MessageType;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WsClient {

    // private WebClient webClient = WebClient.builder()
    //                                         .baseUrl("http://localhost:9901")
    //                                         .build();

    ObjectMapper mapper = new ObjectMapper();

    @Value("${config.websocket.subscribe}")
    private String websocketSubscribe;

    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(this::connect); 
        
    }

    public void connect() {
        log.info("[websocket connect] start trying");
        if(webSocketServerCheck()) {
            log.info("[websocket connect] Server health check : UP");
            try {
                Transport webSocketTransport = new WebSocketTransport(new StandardWebSocketClient());
                List<Transport> transports = Collections.singletonList(webSocketTransport);
                
                WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();

                StompHeaders stompHeaders = new StompHeaders();
                stompHeaders.add("userId", "back-end");

                SockJsClient sockJsClient = new SockJsClient(transports);
                WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
                stompClient.setMessageConverter(new MappingJackson2MessageConverter());
                // stompClient.setMessageConverter(new StringMessageConverter());
                // stompClient.setMessageConverter(new ByteArrayMessageConverter());

                StompSession stompSession = null;
                stompSession = stompClient.connect("http://localhost:9901/websocket", httpHeaders, stompHeaders, new WsClientHandler()).get();

            } catch (Exception e) {
                sleepAndConnect();
            } 
        }
        else{
            log.info("[websocket connect] Server health check : DOWN");
            sleepAndConnect();
        }
        
    }

    private void sleepAndConnect() {
        log.info("[websocket connect] Sleep and connect retry");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e1) {
            // TODO: handle exception
        } finally{
            connect();
        }
    }

    private boolean webSocketServerCheck() {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://localhost:9901")
                    .build();
            String response = webClient.get().uri("/ccfe/health").retrieve().bodyToMono(String.class).block();
            JsonNode responseJson = mapper.readTree(response);
            log.info("{}",response);
            if("UP".equals(responseJson.get("status").textValue())){
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private class WsClientHandler extends StompSessionHandlerAdapter{

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {

            if(!StringUtils.isBlank(websocketSubscribe)){
                String[] websocketSubscribes = StringUtils.split(websocketSubscribe, "|");
                for(String subscribe : websocketSubscribes){
                    session.subscribe("/topic/" + subscribe, this);
                }
            }
            // session.subscribe("/topic/chat", this);
            // session.subscribe("/topic/notice", this);
            // session.subscribe("/topic/system", this);

            ChatMessage chatMessage = ChatMessage.createChatMessage(MessageType.NOTICE, "Back-End서버", "안녕", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy. M. d a h:mm:ss")));
            session.send("/app/chat/notice", chatMessage);

            String userId = connectedHeaders.getFirst("userId");
            log.info("New session: {} | {}", session.getSessionId(), userId);
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            // exception.printStackTrace();
            super.handleException(session, command, headers, payload, exception);
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return ChatMessage.class;
            // return super.getPayloadType(headers);
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            ChatMessage chatMessage = (ChatMessage) payload;

            if(ChatMessage.MessageType.ENTER.equals(chatMessage.getType())){
                log.info("[ENTER] [{}] >>> {}", headers.getDestination(), chatMessage.toString());
            }
            else if(ChatMessage.MessageType.CHAT.equals(chatMessage.getType())){
                log.info("[CHAT] [{}] >>> {}", headers.getDestination(), chatMessage.toString());
            }
            else if(ChatMessage.MessageType.NOTICE.equals(chatMessage.getType())){
                log.info("[NOTICE] [{}] >>> {}", headers.getDestination(), chatMessage.toString());
            }
            else if(ChatMessage.MessageType.QUIT.equals(chatMessage.getType())){
                log.info("[QUIT] [{}] >>> {}", headers.getDestination(), chatMessage.toString());
            }

        }
    }
}
