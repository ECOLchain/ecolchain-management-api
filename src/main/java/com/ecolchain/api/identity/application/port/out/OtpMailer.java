package com.ecolchain.api.identity.application.port.out;

public interface OtpMailer {
    void send(String to, String code, String lang);
}
