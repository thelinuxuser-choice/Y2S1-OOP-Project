package com.parkingsystem.inquiry;

public class EscalatedState implements InquiryState {

    @Override
    public String name() {
        return "ESCALATED";
    }

    @Override
    public void startProgress(Inquiry ctx) {
        ctx.setState(new InProgressState());
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
        // already escalated
    }

    @Override
    public void reopen(Inquiry ctx) {
        throw new IllegalStateException("Already open with staff");
    }

    @Override
    public boolean canReply() {
        return true;
    }
}
