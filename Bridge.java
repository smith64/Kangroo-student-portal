package com.kangaroo;

public class Bridge {
    // Called from JS: returns the email on success or null on failure
    public String login(String email, String password) {
        try {
            String authenticated = DB.authenticateUser(email, password);
            return authenticated; // email on success, null on failure
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

