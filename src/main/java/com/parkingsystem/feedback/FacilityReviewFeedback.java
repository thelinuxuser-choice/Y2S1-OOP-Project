package com.parkingsystem.feedback;

public class FacilityReviewFeedback extends FeedbackEntity {

    public FacilityReviewFeedback() {
        setFeedbackType("FACILITY_REVIEW");
    }

    @Override
    public void validate() {
        if (getRating() < 1 || getRating() > 5) {
            throw new IllegalArgumentException("Rating must be 1–5");
        }
        if (getCommentText() == null || getCommentText().trim().length() < 10) {
            throw new IllegalArgumentException("Facility review should be a bit more detailed");
        }
    }
}
