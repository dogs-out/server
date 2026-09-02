package com.dogsout.server.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param emailSent {@code false} when the message this response is about could not
 *        be delivered to the mail provider. Null whenever no mail was involved, so
 *        it stays out of the JSON for every other endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageResponse(String message, Boolean emailSent) {

    public MessageResponse(String message) {
        this(message, null);
    }
}
