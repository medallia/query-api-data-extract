package com.medallia.references.feedbackdatasync.model;

import java.util.Map;
import java.util.List;

/**
 * A Query API response.  This object hierarchy can be extended based on your
 * particular data shaping needs.  This version captures the most basic
 * structure needed to show how synchronization works.
 */
public class QueryApiResponse {

    private DataWrapper data;
    private List<Object> errors;

    public DataWrapper getData() {
        return this.data;
    }

    public void setData(final DataWrapper data) {
        this.data = data;
    }

    public List<Object> getErrors() {
        return this.errors;
    }

    public void setErrors(final List<Object> errors) {
        this.errors = errors;
    }

    public static class DataWrapper {
        private FeedbackWrapper feedback;

        public FeedbackWrapper getFeedback() {
            return this.feedback;
        }

        public void setFeedback(final FeedbackWrapper feedback) {
            this.feedback = feedback;
        }
    }

    public static class FeedbackWrapper {
        private Long totalCount;
        private List<Map<String, NodeValuesWrapper>> nodes;

        public Long getTotalCount() {
            return this.totalCount;
        }

        public void setTotalCount(final Long totalCount) {
            this.totalCount = totalCount;
        }

        public List<Map<String, NodeValuesWrapper>> getNodes() {
            return this.nodes;
        }

        public void setNodes(final List<Map<String, NodeValuesWrapper>> nodes) {
            this.nodes = nodes;
        }
    }

    public static class NodeValuesWrapper {
        private List<String> values;

        public List<String> getValues() {
            return this.values;
        }

        public void setValues(final List<String> values) {
            this.values = values;
        }
    }

}
