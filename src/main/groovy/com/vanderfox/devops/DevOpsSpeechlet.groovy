package com.vanderfox.devops

import com.amazon.speech.slu.Intent
import com.amazon.speech.slu.Slot
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.Speechlet
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import com.amazon.speech.ui.SimpleCard
import com.amazon.speech.ui.SsmlOutputSpeech
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.CreateStackResult
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.codedeploy.AmazonCodeDeployClient
import com.amazonaws.services.codedeploy.model.GetApplicationRequest
import com.amazonaws.services.codedeploy.model.GetApplicationResult
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.GetTopicAttributesResult
import com.amazonaws.services.sns.model.ListTopicsResult
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.model.PublishResult
import com.amazonaws.services.sns.model.Topic
import groovy.transform.CompileStatic
import org.slf4j.Logger;
import org.slf4j.LoggerFactory

/**
 * This app shows how to connect to devops with Spring Social, Groovy, and Alexa.
 * @author Lee Fox and Ryan Vanderwerf
 */
@CompileStatic
public class DevOpsSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(DevOpsSpeechlet.class);
    private boolean keepRunning = false

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        initializeComponents(session)

        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        getWelcomeResponse(session);
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;
        log.debug("Intent = " + intentName)
        switch (intentName) {
            case "cloudwatchAlarms":
                cloudwatchAlarms()
                break
            case "countRunningInstances":
                countInstances("running")
                break
            case "deployTomcat":
                deployTomcat("")
                break
            case "buildCloudformation":
                buildCloudformation("")
                break
            case "countInstances":
                Slot slot = intent.getSlot("instanceState")
                String slotValue = slot.getValue()
                countInstances(slotValue)
                break
            case "sendSnsNotification":
                sendSnsNotification("message")
                break
            case "AMAZON.HelpIntent":
                getHelpResponse(session)
                break
            case "AMAZON.CancelIntent":
                sayGoodbye()
                break
            case "AMAZON.StopIntent":
                sayGoodbye()
                break
            default:
                didNotUnderstand()
                break
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getWelcomeResponse(Session session) {
        String speechText = "This is the DevOps Assistant.  I will try to help with your DevOps needs.  Say help or cancel to stop, or help if you need assistance.";
        keepRunning = true
        askResponse(speechText, speechText)
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse sayGoodbye() {
        String speechText = "OK.  I'm going to stop now.";
        tellResponse(speechText, speechText)
    }

    private SpeechletResponse cloudwatchAlarms() {
        AWSCredentials credentials = new BasicAWSCredentials("AKIAJLNQS6XTH3ESTCBQ", "4Ra3dZl9SAiY0PudxqWQUOmhIIY0JpYUW4ZfdWu+")
        AmazonCloudWatchClient cloudWatchClient = new AmazonCloudWatchClient(credentials)
        cloudWatchClient.setEndpoint("monitoring.amazonaws.com")
        DescribeAlarmsResult result = cloudWatchClient.describeAlarms()
        List<MetricAlarm> alarms = result.getMetricAlarms()
        int alarmCount = alarms.size()
        String speechText = "I currently see ${alarmCount} CloudWatch Alarms.\n\n"
        keepRunning ? askResponse(speechText, speechText) : tellResponse(speechText, speechText)
    }

    private SpeechletResponse sendSnsNotification(String message) {
        AWSCredentials credentials = new BasicAWSCredentials("AKIAJLNQS6XTH3ESTCBQ", "4Ra3dZl9SAiY0PudxqWQUOmhIIY0JpYUW4ZfdWu+")
        AmazonSNSClient snsClient = new AmazonSNSClient(credentials)
        snsClient.setEndpoint("sns.us-east-1.amazonaws.com")
        ListTopicsResult listTopicsResult = snsClient.listTopics()
        List<Topic> topics = listTopicsResult.getTopics()
        String topicArn = ""
        for(Topic currentTopic: topics) {
            log.debug("currentTopic.topicArn:  " + currentTopic.topicArn)
            if(currentTopic.topicArn.contains("lambdaNotification")) {
                topicArn = currentTopic.getTopicArn()
            }
        }
        log.debug("topicArn" + topicArn)
        PublishRequest publishRequest = new PublishRequest(topicArn, message)
        snsClient.publish(publishRequest)
        String speechText = "OK, I've published your notification"
        keepRunning ? askResponse(speechText, speechText) : tellResponse(speechText, speechText)
    }

    private SpeechletResponse deployTomcat(String instanceState) {

        AWSCredentials credentials = new BasicAWSCredentials("AKIAJLNQS6XTH3ESTCBQ", "4Ra3dZl9SAiY0PudxqWQUOmhIIY0JpYUW4ZfdWu+")
        AmazonCodeDeployClient codeDeployClient = new AmazonCodeDeployClient(credentials)
        GetApplicationRequest getApplicationRequest = new GetApplicationRequest()
        getApplicationRequest.setApplicationName("tomcat")
        GetApplicationResult getApplicationResult = codeDeployClient.getApplication(getApplicationRequest)

        String speechText = "OK, I've deployed Tomcat"
        keepRunning ? askResponse(speechText, speechText) : tellResponse(speechText, speechText)
    }

    private SpeechletResponse buildCloudformation(String instanceState) {
        AWSCredentials credentials = new BasicAWSCredentials("AKIAJLNQS6XTH3ESTCBQ", "4Ra3dZl9SAiY0PudxqWQUOmhIIY0JpYUW4ZfdWu+")
        AmazonCloudFormationClient cloudFormationClient = new AmazonCloudFormationClient(credentials)
        CreateStackRequest createStackRequest = new CreateStackRequest()
        createStackRequest.setStackName("Test")
        createStackRequest.setTemplateURL("https://s3.amazonaws.com/cf-templates-1gc271oppb1j1-us-east-1/2016330HaY-CF.template")
        ArrayList<Parameter> parameters = new ArrayList<>()
        Parameter typeParameter = new Parameter()
        typeParameter.setParameterKey("InstanceType")
        typeParameter.setParameterValue("t2.medium")
        parameters.add(typeParameter)
        Parameter keyParameter = new Parameter()
        keyParameter.setParameterKey("KeyName")
        keyParameter.setParameterValue("lfox")
        parameters.add(keyParameter)
        Parameter sshLocationParameter = new Parameter()
        sshLocationParameter.setParameterKey("SSHLocation")
        sshLocationParameter.setParameterValue("0.0.0.0/0")
        parameters.add(sshLocationParameter)
        createStackRequest.withParameters(parameters)
        CreateStackResult createStackResult = cloudFormationClient.createStack(createStackRequest)

        String speechText = "OK, I've started building your stack.  Give it a few minutes to complete."
        keepRunning ? askResponse(speechText, speechText) : tellResponse(speechText, speechText)
    }

    private SpeechletResponse countInstances(String instanceState) {

        int instanceCount = 0
        AWSCredentials credentials = new BasicAWSCredentials("AKIAJLNQS6XTH3ESTCBQ", "4Ra3dZl9SAiY0PudxqWQUOmhIIY0JpYUW4ZfdWu+")
        AmazonEC2Client ec2Client = new AmazonEC2Client(credentials);
        ec2Client.setEndpoint("ec2.amazonaws.com")
        Filter[] filters = new Filter[1]
        filters[0] = new Filter().withName("instance-state-name").withValues(instanceState)
        DescribeInstancesRequest request = new DescribeInstancesRequest()
        request.setFilters(filters.toList())
        DescribeInstancesResult result = ec2Client.describeInstances(request)
        List<Reservation> reservations = result.getReservations()
        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances()
            for(Instance currentInstance: instances) {
                instanceCount++
            }
        }

        String speechText = "I currently see ${instanceCount} instances ${instanceState}.\n\n"
        keepRunning ? askResponse(speechText, speechText) : tellResponse(speechText, speechText)
    }

    private SpeechletResponse askResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("DevOps Assistant");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    private SpeechletResponse tellResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("DevOps Assistant");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        SpeechletResponse.newTellResponse(speech, card);
    }

    private SpeechletResponse askResponseFancy(String cardText, String speechText, String fileUrl) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("DevOps Assistant");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        log.info("making welcome audio")
        SsmlOutputSpeech fancySpeech = new SsmlOutputSpeech()
        fancySpeech.ssml = "<speak><audio src=\"${fileUrl}\"/> ${speechText}</speak>"
        log.info("finished welcome audio")
        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(fancySpeech);

        SpeechletResponse.newAskResponse(fancySpeech, reprompt, card);
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelpResponse(Session session) {
        String speechText = ""
        speechText = "You can say stop or cancel to stop at any time.  You can ask me for the core values or the 12 principles.";
        askResponse(speechText, speechText)
    }

    private SpeechletResponse didNotUnderstand() {
        String speechText = "I'm sorry.  I didn't understand what you said.";
        askResponse(speechText, speechText)
    }

    /**
     * Initializes the instance components if needed.
     */
    private void initializeComponents(Session session) {
    }
}
