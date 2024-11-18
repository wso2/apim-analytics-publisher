package org.wso2.am.analytics.retriever.choreo.model;

import feign.Headers;
import feign.RequestLine;

public interface GraphQLClient {
    @RequestLine("POST")
    @Headers("Content-Type: application/json")
    public graphQLResponseClient getSuccessAPIsUsageByApplications(GraphqlQueryModel graphqlQueryModel);
}
