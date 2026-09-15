package com.parkingsystem.feedback;

public class RatingWithCommentFeedback extends FeedbackEntity {

    public RatingWithCommentFeedback() {
        setFeedbackType("RATING_WITH_COMMENT");
    }

    @Override
    public void validate() {
        if (getRating() < 1 || getRating() > 5) {
            throw new IllegalArgumentException("Rating must be 1–5");
        }
        if (getCommentText() == null || getCommentText().trim().length() < 3) {
            throw new IllegalArgumentException("Comment needs at least a few words");
        }
    }
}
