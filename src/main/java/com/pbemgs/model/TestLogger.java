package com.pbemgs.model;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

// Test logger to flip prints off and on
public class TestLogger implements LambdaLogger {
    public boolean on = false;

    public void log(String message) {
        if (on) System.out.println("Log: " + message);
    }

    public void log(byte[] message) {
    }
}
