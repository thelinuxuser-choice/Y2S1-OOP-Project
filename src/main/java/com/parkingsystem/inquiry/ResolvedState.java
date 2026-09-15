package com.parkingsystem.inquiry;

public class ResolvedState implements InquiryState {

    @Override
    public String name() {
        return "RESOLVED";
    }

    @Override
    public void startProgress(Inquiry ctx) {
        throw new IllegalStateException("Already resolved — reopen instead");
    }

    @Override
    public void resolve(Inquiry ctx) {
        // no-op
    }

    @Override
    public void close(Inquiry ctx) {
        ctx.setState(new ClosedState());
    }

    @Override
    public void escalate(Inquiry ctx) {
        throw new IllegalStateException("Resolve closed the queue — reopen first");
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
