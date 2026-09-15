package com.parkingsystem.inquiry;

public class InProgressState implements InquiryState {

    @Override
    public String name() {
        return "IN_PROGRESS";
    }

    @Override
    public void startProgress(Inquiry ctx) {
        // already here
    }

    @Override
    public void resolve(Inquiry ctx) {
        ctx.setState(new ResolvedState());
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
        throw new IllegalStateException("Already in progress");
    }

    @Override
    public boolean canReply() {
        return true;
    }
}
