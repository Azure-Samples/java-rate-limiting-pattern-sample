using './main.bicep'

param location = readEnvironmentVariable('LOCATION', 'uksouth')
param resourceGroupName = readEnvironmentVariable('RESOURCE_GROUP_NAME', 'rate-limiting-sample-rg')
param topicName = readEnvironmentVariable('QUEUE_TOPIC_NAME', 'rate-limiting-sample-fifo-topic')
param topicSubscriptionName = readEnvironmentVariable('QUEUE_TOPIC_SUBSCRIPTION_NAME', 'rate-limiting-sample-fifo-topic-subs')
