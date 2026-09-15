package com.parkingsystem.inquiry;

public class OpenState implements InquiryState {

    @Override
    public String name() {
        return "OPEN";
    }

    @Override
    public void startProgress(Inquiry ctx) {
        ctx.setState(new InProgressState());
    }

    @Override
    public void resolve(Inquiry ctx) {
        throw new IllegalStateException("Pick up the ticket first (move to In Progress)");
    }

    @Override
    public void close(Inquiry ctx) {
        ctx.setState(new ClosedState());
    }

    @Override
    public void escalate(Inquiry ctx) {
        ctx.setState(new EscalatedState());
    }

    @Override
    public void reopen(Inquiry ctx) {
        throw new IllegalStateException("Already open");
    }

    @Override
    public boolean canReply() {
        return true;
    }
}
