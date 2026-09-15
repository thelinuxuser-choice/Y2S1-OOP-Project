package com.parkingsystem.feedback;

public class RatingOnlyFeedback extends FeedbackEntity {

    public RatingOnlyFeedback() {
        setFeedbackType("RATING_ONLY");
    }

    @Override
    public void validate() {
        if (getRating() < 1 || getRating() > 5) {
            throw new IllegalArgumentException("Rating must be 1–5");
        }
        setCommentText(null);
    }
}
