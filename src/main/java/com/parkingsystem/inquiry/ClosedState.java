package com.parkingsystem.inquiry;

public class ClosedState implements InquiryState {

    @Override
    public String name() {
        return "CLOSED";
    }

    @Override
    public void startProgress(Inquiry ctx) {
        throw new IllegalStateException("Ticket is closed — reopen first");
    }

    @Override
    public void resolve(Inquiry ctx) {
        throw new IllegalStateException("Ticket is closed");
    }

    @Override
    public void close(Inquiry ctx) {
        // already closed
    }

    @Override
    public void escalate(Inquiry ctx) {
        throw new IllegalStateException("Ticket is closed");
    }

    @Override
    public void reopen(Inquiry ctx) {
        ctx.setState(new InProgressState());
    }

    @Override
    public boolean canReply() {
        return false;
    }
}
