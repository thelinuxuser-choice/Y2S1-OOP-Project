package com.parkingsystem.feedback;

// picks the right feedback subtype from what the form sent
public class FeedbackFactory {

    public FeedbackEntity create(String type) {
        if (type == null) {
            type = "RATING_ONLY";
        }
        switch (type.toUpperCase()) {
            case "RATING_WITH_COMMENT":
                return new RatingWithCommentFeedback();
            case "FACILITY_REVIEW":
                return new FacilityReviewFeedback();
            case "RATING_ONLY":
            default:
                return new RatingOnlyFeedback();
        }
    }
}
