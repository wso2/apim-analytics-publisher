package org.wso2.am.analytics.retriever.choreo.model;

import com.google.gson.annotations.SerializedName;
import org.json.simple.JSONObject;

public class GraphqlQueryModel {

    @SerializedName("query")
    private String query;

    @SerializedName("variables")
    private JSONObject variables;

    public String getQuery() {

        return query;
    }

    public JSONObject getVariables() {

        return variables;
    }

    public void setQuery(String query) {

        this.query = query;
    }

    public void setVariables(JSONObject variables) {

        this.variables = variables;
    }
}
