package nl.markhayen.desktop.model;

import java.util.List;

public record RunScriptResponse(Boolean done, RunError error, RunResponse response) {
    public record RunResponse(String result) {
    }
    public record RunError(Integer code, String message, List< ExecutionError> details) {
        public record  ExecutionError(String errorMessage, String errorType) {
        }
    }

}




