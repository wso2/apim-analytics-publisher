package org.wso2.am.analytics.retriever.choreo.model;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Util;

public class QueryAPIAccessTokenInterceptor implements RequestInterceptor {
    private String accessToken;

    public QueryAPIAccessTokenInterceptor(String accessToken) {
        Util.checkNotNull(accessToken, "accessToken", new Object[0]);
        this.accessToken = accessToken;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        requestTemplate.header("Authorization", "Bearer ".concat(accessToken));
    }
}
