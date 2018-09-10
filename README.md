# Real-time bushfire alerting with Complex Event Processing (CEP) in Apache Flink on Amazon EMR and IoT sensor network

This repository contains the code and documentations for the demonstration example of the real-time bushfire alerting with the Complex Event Processing (CEP) in Apache Flink on Amazon EMR and a simulated IoT sensor network as described on the [AWS Big Data Blog](https://aws.amazon.com/blogs/big-data/real-time-bushfire-alerting-with-complex-event-processing-in-apache-flink-on-amazon-emr-and-iot-sensor-network/).

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. For launching this setup using an AWS CloudFormation template please use the [cfn-template.yml](https://github.com/aws-samples/realtime-bushfire-alert-with-apache-flink-cep/blob/master/cfn-template/cfn-template.yml) file located under the `cfn-template/` directory instead.

### Prerequisites

To compile and build this project from the source you need to install [Gradle](https://gradle.org/) build system in your local machine. If you are using Mac OSX then you can install Gradle simply using [Brew](https://brew.sh/).

```
% brew install gradle
```

For installation in other OS environment, please refer to the [installation notes](https://gradle.org/install/) in the Gradle Web site.

### Installation and Usage

Once you download the source files into your local machine, you need to build both the [iot-simulator](https://github.com/aws-samples/realtime-bushfire-alert-with-apache-flink-cep/tree/master/iot-simulator) and [flink-cep-consumer](https://github.com/aws-samples/realtime-bushfire-alert-with-apache-flink-cep/tree/master/flink-cep-consumer) packages to get the producer and consumer applications, respectively. You can simply run the below command to compile and build the application JAR file upon navigating to the individual package directory.

```
% gradle clean uber
```

The packaged application JAR files (named as ```iot-simulator-latest.jar``` and ```flink-cep-consumer-latest.jar```, respectively) can be found navigating to the ```/build/libs/``` location of the respective directories upon successful compilation.

You can run the ```iot-simulator-latest.jar``` file with ```--help or -h``` option to view the usage options for this producer package.

```
% java -jar iot-simulator-latest.jar --help
```

This will give the following output with the (*) options as the mandatory arguments to pass:

```
Usage: <main class> [options]
  Options:
  * --aws-iot-endpoint, -endpoint
      AWS IoT endpoint: <endpoint-url>
  * --aws-iot-topic, -topic
      AWS IoT topic name: <topic-name>
  * --certificate, -cert
      AWS IoT client certificate: <certificate-filename>
    --debug, -d
      Enable debug logs: [true, false]
      Default: false
    --help, -h
      Help
    --interval, -i
      Metric interval period in minute: <number>
      Default: 1
  * --private-key, -pk
      AWS IoT client private key: <key-filename>
    --sensors, -s
      Sensor count: <number>
      Default: 11
```

The producer application can be run from your local machine or even from an Amazon EC2 instance providing that you supply all the required arguments like the AWS IoT endpoint, IoT topic name, and the certificate and primary key files. The consumer application ```flink-cep-consumer-latest.jar``` on the other hand needs to be used with the Apache Flink environment like the one comes with Amazon EMR cluster setup.

In the next sections we discuss how to setup the individual components of this particular architecture if you are not using the provided AWS CloudFormation template. Note that, for explanation purpose we assume the default AWS region to be US East (N. Virginia) i.e., us-east-1 in the following sections. We also assume that all the files are located under the user's UNIX home directory `~`. Additionally, please pay special attention to replace all the placeholder texts within `<>` or `${}` with the appropriate ones according to your particular setup.

## Setup Amazon IoT Core Services

### Amazon IoT custom endpoint
To get started with AWS IoT Core, first, you need to get the custom IoT endpoint already provisioned for the AWS user. You can go to the `Settings` page of the AWS IoT Services from the AWS Management Console following the link [here](https://us-east-1.console.aws.amazon.com/iot/home?region=us-east-1#/settings) and copy the endpoint URL located under the `Custom endpoint` section there which shuold be looked eventTime similar to `XXXXX.iot.us-east-1.amazonaws.com`

### Amazon IoT rule for MQTT topic
Before creating the IoT rule, one needs to create an appropriate IAM action role first that the rule will later leverage on.

#### IAM role with custom policy for Amazon IoT Core

You need to create an IAM role named `iot-action-role` which will be a STS Assume Role allowing AWS service `iot.amazonaws.com` by using the trust policy document `iot-action-role-trust-policy.json` with the AWS managed policy `AWSIoTLogging` and the custom policy file named `iot-action-role-policy.json` provided under the `iam/` directory here. One can also follow this [link](https://docs.aws.amazon.com/iot/latest/developerguide/iot-create-role.html) to properly create this role.

```
% aws iam create-role --role-name iot-action-role --assume-role-policy-document file://~/iot-action-role-trust-policy.json
% aws iam attach-role-policy --role-name iot-action-role --policy-arn "arn:aws:iam::aws:policy/service-role/AWSIoTLogging"
% aws iam put-role-policy --role-name iot-action-role --policy-name iot-action-role-policy --policy-document file://~/iot-action-role-policy.json
```

#### Creating IoT Topic rule
Once the endpoint and IAM role are in place, next we need to create an IoT rule by using the `iot-action-rule.json` file under the `iot/` directory here for our MQTT topic named `weather-sensor-data`. Note that, we do not need to explicitly create this MQTT topic since it will be automatically created once we start to publish IoT messages under this topic name.

```
% aws iot create-topic-rule --rule-name IoTActionRule --topic-rule-payload file://~/iot-action-rule.json
```

Additionally, you can take a look at this [link](https://docs.aws.amazon.com/iot/latest/developerguide/iot-create-rule.html) for further details on how to create IoT rules in Amazon IoT.

### Amazon IoT keys and certificate

Finally, to interact with the custom AWS IoT endpoint you need to create a pair of certificate and keys so that the emulated sensor nodes can make successful connections to the Amazon IoT.

```
% aws iot create-keys-and-certificate --set-as-active --certificate-pem-outfile weather-sensor-certificate.pem.crt --public-key-outfile weather-sensor-public.pem.key --private-key-outfile weather-sensor-private.pem.key
```

You should copy or move the certificate and key files to the directory where we will be running the IoT simulator from. Once created you can also view and manage the certificate by visiting the [Certificates](https://us-east-1.console.aws.amazon.com/iot/home?region=us-east-1#/certificatehub) section in your AWS Management Console for Amazon IoT Core.

## Setup Amazon Kinesis Data Streams
In this section, we will setup a Amazon Kinesis Data Stream named `weather-sensor-data-stream` with a single shard that will be sufficient to handle the streaming load for this setup.

```
% aws kinesis create-stream --stream-name weather-sensor-data-stream --shard-count 1 --region us-east-1
```

## Setup Amazon Elasticsearch Service domain for visualization

For the purpose of visualization, we need to setup a single data node only Amazon Elasticsearch Service domain running on the latest Elasticsearch version 6.3. You should secure the access into the domain by restricting its access by a single IAM user of your choice. Otherwise, you can also restrict the access from a specific IP address. For the purpose of this demonstration we will add a custom IAM policy to allow the `EMR_EC2_DefaultRole` to bulk write the documents into this domain from our Apache Flink CEP consumer program. A sample access policy named `access-policy.json` is provided under the `elasticsearch\` directory which allows both read and write access to the domain.

```
% aws es create-elasticsearch-domain --domain-name weather-sensor-documents --elasticsearch-version 6.3 --elasticsearch-cluster-config InstanceType=m3.medium.elasticsearch,InstanceCount=1 --access-policies file://~/access-policy.json --region us-east-1
```

Once the domain is up and running, go to the Kibana dashboard and to the `Dev Tools` section, and create a new index called `sensor-readings` using the below command. The index mapping is provided in the `mapping.json` file under the `elasticsearch/` directory here.

```json
PUT sensor-readings
{
  "settings": {
        "number_of_shards" :   1,
        "number_of_replicas" : 0
  },
  "mappings": {
    "sensor": {
      "properties" : {
        "node" : { "type" : "integer"},
        "event" : { "type" : "integer" },
        "batch" : { "type" : "integer" },
        "eventTime" : {
          "type" : "date",
          "format": "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
        },
        "temperature" : { "type" : "double" },
        "infectedBy" : { "type" : "integer" }
      }
    }
  }
}

GET sensor-readings
```

Next,

## Setup Amazon SNS topic for alerting

As part of the Apache Flink CEP pattern detection for a real-time bushfire, a SNS alert email will be sent to a designated email address. To set this up, we need to have a SNS topic in place and subscribe the topic for email notifications following this documentation [link](https://docs.aws.amazon.com/cli/latest/userguide/cli-sqs-queue-sns-topic.html).

```
% aws sns create-topic --name weather-sensor-sns-topic
```
```
% aws sns subscribe --topic-arn "<sns-topic-arn>" --protocol email --notification-endpoint "<email-address>"
```

## Setup Amazon EMR cluster with Apache Flink

Finally, to complete our infrastructure setup, we need to create an Amazon EMR cluster with Apache Flink only. Here, we will be using the latest EMR release version 5.16.0 which supports Apache Flink version 1.5.0. The details of the relevant release versions can be found in this [link](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-release-5x.html#emr-5160-release).

Run the below CLI command to create the EMR cluster with all of its required parameters.

```
% aws emr create-cluster \
--auto-scaling-role EMR_AutoScaling_DefaultRole  \  
--termination-protected  \  
--applications Name=Hadoop Name=Flink \   
--ebs-root-volume-size 10  \  
--ec2-attributes '{"KeyName":"<iam-user-key-name>","InstanceProfile":"EMR_EC2_DefaultRole","SubnetId":"<subnet-id>","EmrManagedSlaveSecurityGroup":"<emr-slave-security-group-id>","EmrManagedMasterSecurityGroup":"<emr-master-security-group-id>"}'  \   
--service-role EMR_DefaultRole  \  
--enable-debugging  \  
--release-label emr-5.16.0  \
--log-uri 's3n://aws-logs-<aws-account-id>-us-east-1/elasticmapreduce/'  \  
--name 'weather-sensor-flink-cep-emr-5160' --instance-groups '[{"InstanceCount":2,"EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"SizeInGB":32,"VolumeType":"gp2"},"VolumesPerInstance":1}]},"InstanceGroupType":"CORE","InstanceType":"m4.large","Name":"Core - 2"},{"InstanceCount":1,"EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"SizeInGB":32,"VolumeType":"gp2"},"VolumesPerInstance":1}]},"InstanceGroupType":"MASTER","InstanceType":"m4.large","Name":"Master - 1"}]'  \
--scale-down-behavior TERMINATE_AT_TASK_COMPLETION  \  
--steps Type=CUSTOM_JAR,Jar=command-runner.jar,Name=Realtime_Bushfire_Alert_with_Apache_Flink_CEP,Args="flink-yarn-session -n 1 -s 1 -tm 4096 -d" \
--region us-east-1
```

## Running the consumer and producer applications

### Run the Apache Flink CEP consumer application

Assuming all the above setups are completed we are now ready to run the consumer and producer applications. you need to run the uber JAR file as an Apache Flink application in the already running Amazon EMR cluster. This [guide](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/flink-jobs.html) provides various ways to do this, however, for simplicity we will only show two of the possible ways below.

#### Run the consumer application from the command-line in the Amazon EMR cluster's master node

To run the Flink CEP consumer JAR application, you can login to the EMR master node and run it using the below command:

```
% flink run -p 1 flink-cep-consumer-latest.jar --region <aws-region-code> --stream <stream-name> --es-endpoint <es-domain-endpoint> --sns-topic-arn <sns-topic-arn>
```

#### Run the consumer application from the Apache Flink's Web UI in Amazon EMR

You can also submit a Apache Flink application JAR from using the Web UI which is quite convenient during development and testing phase. First, make sure that you can access the `YARN ResourceManager` Web UI at `http://master-public-dns-name:8088/` for your EMR cluster following this documentation [link](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-web-interfaces.html).

Once you are in the `YARN ResourceManager` page, then click to select the Apache Flink application that is already running, and then click the `ApplicationMaster` URL to go the `Apache Flink Web Dashboard`. Finally, click the `Submit new Job` option from the menu on the left hand side. Once you have uploaded the consumer JAR file, you will have the option to set the below parameters as the `Program Arguments`. You can also set the `Parrallism` value to `2`, otherwise, it will be defaulted to `1` which is sufficient to run the consumer application for this blog.

```
--region us-east-1 --stream <stream-name> --es-endpoint <es-domain-endpoint> --sns-topic-arn <sns-topic-arn>
```

### Run the IoT simulator producer application
Finally, to run the simulator JAR you need to provide the required arguments as below:

```
% java -jar iot-simulator-latest.jar -endpoint <aws-iot-endpoint> -topic <aws-iot-topic> -cert <aws-iot-certificate-file-name> -pk <aws-iot-private-key-file-name> -d
```


## Optional: Building the Apache Flink Kinesis connector

Apache Flink Kinesis connector is not part of the official release due to licensing issue, therefore, one have to build it from source to programmatically connect to Amazon Kinesis Data Streams service providing the proper AWS SDK and Hadoop release versions in the corresponding Amazon EMR release based on the documentation [here](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-release-5x.html#emr-5160-release) that you are going to use as well as the latest AWS [KCL](https://github.com/awslabs/amazon-kinesis-client) and [KPL](https://github.com/awslabs/amazon-kinesis-producer) release versions. For the convenience of the end-users, here we have included the complied connector library for the Apache Flink version 1.5.0.

```
% wget -qO- https://github.com/apache/flink/archive/release-1.5.0.zip | bsdtar -xf-
% cd flink-release-1.5.0
% mvn clean package -Pinclude-kinesis -DskipTests -Dhadoop-two.version=2.8.4 -Daws.sdk.version=1.11.336 -Daws.kinesis-kcl.version=1.9.0 -Daws.kinesis-kpl.version=0.12.9
```

Once the compilation process is completed, then the packaged connector library will be found under the `flink-connectors/flink-connector-kinesis/target/` directory. You should add the connector to your local Maven repository and also add dependency details for this library into your POM.xml or gradle.build file accordingly.

```
% mvn install:install-file -Dfile=libs/flink-connector-kinesis_2.11-1.5.0.jar -DgroupId=org.apache.flink -DartifactId=flink-connector-kinesis_2.11 -Dversion=1.5.0 -Dpackaging=jar
```

**POM.xml**
```xml
    <dependency>
        <groupId>org.apache.flink</groupId>
        <artifactId>flink-connector-kinesis_2.11</artifactId>
        <version>1.5.0</version>
        <scope>compile</scope>
    </dependency>
```

**build.gradle**
```
compile group: 'org.apache.flink', name: 'flink-connector-kinesis_2.11', version: '1.5.0'
```

For the purpose of this blog, you should not need to build this connector by yourself as its been already provided with the flink-cep-consumer package and will be bundled inside the uber JAR when you will build the package using Gradle. Further details can be found [here](https://ci.apache.org/projects/flink/flink-docs-master/dev/connectors/kinesis.html).