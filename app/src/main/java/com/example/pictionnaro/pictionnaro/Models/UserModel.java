package com.example.pictionnaro.pictionnaro.Models;

/**
 * Created by jordan on 07/11/17.
 */

public class UserModel {
    private String email;
    private String username;

    public UserModel(String email, String username) {
        this.email = email;
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
