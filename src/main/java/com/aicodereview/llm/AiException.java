package com.aicodereview.llm;

/**
 * A user-presentable failure of the AI request. Messages must never contain the API key.
 */
public class AiException extends Exception {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
