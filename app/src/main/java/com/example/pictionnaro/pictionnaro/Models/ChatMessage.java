package com.example.pictionnaro.pictionnaro.Models;

import java.util.Date;

/**
 * Created by jordan on 07/11/17.
 */

public class ChatMessage {

    private String messageText;
    private String userName;
    private long messageTime;

    public ChatMessage(String messageText, String userName) {
        this.messageText = messageText;
        this.userName = userName;

        // Initialize to current time
        messageTime = new Date().getTime();
    }

    public ChatMessage(){

    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public long getMessageTime() {
        return messageTime;
    }

    public void setMessageTime(long messageTime) {
        this.messageTime = messageTime;
    }
}