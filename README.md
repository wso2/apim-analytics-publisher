# apim-analytics-publisher
This repo contains the data publisher for Choreo Analytics cloud. Data publisher is responsible for sending analytics
 data into Azure cloud for processing and visualization. 
 
## Install
### Install from source

- Clone this repository
- Build the source using ```mvn clean install```
- You can find the executable at ```<Repo_Home>/component/data-publisher/target/org.wso2.am.analytics.publisher
.client-x.x.x-jar-with-dependencies.jar```
### Download from releases

- Navigate to release section of this repository and download the desired release

## Acquire SAS Token
To publish events into azure infrastructure users will need a SAS token with necessary privileges. As of this release
 only selected users will be provided access. If you require access please contact one of the contributers. If you
  already have the access to Azure environment please use 
  [this](https://docs.microsoft.com/en-us/rest/api/eventhub/generate-sas-token) guideline to generate your own SAS Token.

## Running the client
In this milestone client is configurable via environment variables and following environment variables are available.

- APIM_ANL_SAS_TOKEN : Mandatory variable to provide the SAS token for the Azure environment
- APIM_ANL_EVENT_COUNT : Optional variable to provide the number of events you need to publish to Azure environment
- APIM_ANL_PUBLISHER : Optional variable to provide number of parallel clients which will publish the events to Azure
 environment. Total events published will be APIM_ANL_EVENT_COUNT*APIM_ANL_PUBLISHER
 
 To run the client follow below steps
 - Set the environment variables as desired.
 Ex: export APIM_ANL_SAS_TOKEN="Your_SAS_Token_here"
 - Run the client by issuing this comment from the location of the built/downloaded jar. ```java -jar org.wso2.am
 .analytics.publisher.client-x.x.x-jar-with-dependencies.jar```