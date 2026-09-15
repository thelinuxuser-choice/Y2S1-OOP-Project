package com.parkingsystem.inquiry;

// state pattern – each status decides what staff/customer can do next
public interface InquiryState {

    String name();

    void startProgress(Inquiry ctx);

    void resolve(Inquiry ctx);

    void close(Inquiry ctx);

    void escalate(Inquiry ctx);

    void reopen(Inquiry ctx);

    boolean canReply();
}
