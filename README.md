# Dynatrace Backend listener plugin for JMeter
This library implements a JMeter Backend listener and sends the recorded loadtest metrics via the Dynatrace MINT metric ingest to the configured Dynatrace monitoring environment.
See https://jmeter.apache.org/usermanual/component_reference.html#Backend_Listener for a description of the JMeter BackendListener concept.  

# Building
Execute the gradle build task. This will generate a "jmeter-dynatrace-plugin-1.0-SNAPSHOT.jar" in the build/libs directory

# Prerequisites
JMeter 5.3 (https://jmeter.apache.org/download_jmeter.cgi)
Dynatrace Version > 1.202

# Installation
Copy this generated jar to the apache-jmeter-5.3\lib\ext folder and (re-)start JMeter

# Configuration
## Dynatrace
* Open the WebUI of your monitoring environment
* Go to `Settings` > `Integration` > `Dynatrace API`
* Select  `Generate token`
* Add a token name and select the access scope `Ingest metrics using API V2`
    * If you also want to read those metrics, you can also select  `Read metrics using API V2`
* Copy the generated token ad store it in a secure space (it will be needed for the JMeter plugin configuration)    
## JMeter
* Open your JMeter jmx file
* Add `Listener` > `Backend Listener`
* Select `Backend Listener implementation`: `com.dynatrace.jmeter.plugins.MintBackendListener`
* Change the parameters:
    * `dynatraceMetricIngestUrl`: the URL of your monitoring environment with the suffix `/api/v2/metrics/ingest`
    * `dynatraceApiToken`: the API token which you generated for your Dynatrace API integration
    * `testDimensions`: a comma-separated list of key=value pairs which will be used as dimensions for the test related metrics. 
    * `transactionDimensions`: a comma-separated list of key=value pairs which will be used as dimensions for the test step related metrics.
* Start the load test    


# MINT metrics

When the JMeter test is running, it will generate the specified general metrics:
* `jmeter.usermetrics.startedthreads`: the number of started threads
* `jmeter.usermetrics.finishedthreads`: the number of finished threads
* `jmeter.usermetrics.minactivethreads`: the minimum number of active threads
* `jmeter.usermetrics.maxactivethreads`: the maximum number of active threads
* `jmeter.usermetrics.meanactivethreads`: the arithmetic mean of active threads

Dimensions used for those metrics:
* `testDimensions`: a comma-separated list of key=value pairs which will be used as dimensions for the test related metrics. e.g. `dimension1=Test1,dimension2=Test2`

In  addition it will generate the specified metrics for each test step (JMeter sampler)
* `jmeter.usermetrics.transaction.mintime`: the minimal elapsed time for requests within sliding window
* `jmeter.usermetrics.transaction.maxtime`:  the maximal elapsed time for requests within sliding window
* `jmeter.usermetrics.transaction.meantime`: the arithmetic mean of the elapsed time
* `jmeter.usermetrics.transaction.receivedbytes`: the number of received bytes
* `jmeter.usermetrics.transaction.sentbytes`: the number of sent bytes
* `jmeter.usermetrics.transaction.hits`: the number of hits to the server
* `jmeter.usermetrics.transaction.error`: the number of failed requests
* `jmeter.usermetrics.transaction.success`: the number of successful requests
* `jmeter.usermetrics.transaction.count`: the total number of requests


Dimensions used for those metrics:
* `transactionDimensions`: a comma-separated list of key=value pairs which will be used as dimensions for the test step related metrics. e.g. `dimension3=Test3,dimension4=Test4`

# Monitoring the metrics in Dynatrace

* Create a dashboard
* For every metric:
    * Add a custom chart
    * Select `Try it out` in the Banner line `Analyze multidimensional metrics from Prometheus, StatsD and others channels right on your dashboards`
    * Enter the name of the metric (from the list of the metrics above) in the field `Filter metrics by...`
    * Specify the chart settings (Visualization type, Chart type...)
    * Select `Pin to dashboard`
* Save the dashboard    
`