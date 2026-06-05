package com.grandstay.hotelos.reception.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public String chat(String message) {
        System.out.println("Keldi: " + message);
        return "OK: " + message;
    }
}