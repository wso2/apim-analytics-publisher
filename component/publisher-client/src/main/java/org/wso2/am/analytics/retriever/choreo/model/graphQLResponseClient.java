package org.wso2.am.analytics.retriever.choreo.model;

import com.google.gson.annotations.SerializedName;
import com.google.gson.internal.LinkedTreeMap;

import java.util.ArrayList;

public class graphQLResponseClient {
    @SerializedName("data")
    private LinkedTreeMap<String, ArrayList<LinkedTreeMap<String, String>>> data = new LinkedTreeMap<>();

    public LinkedTreeMap<String, ArrayList<LinkedTreeMap<String, String>>> getData() {
        return data;
    }

    public void setData(LinkedTreeMap<String, ArrayList<LinkedTreeMap<String, String>>> data) {
        this.data = data;
    }

}
