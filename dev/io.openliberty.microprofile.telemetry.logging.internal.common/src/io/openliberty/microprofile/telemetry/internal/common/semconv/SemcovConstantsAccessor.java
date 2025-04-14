package io.openliberty.microprofile.telemetry.internal.common.semconv;

import io.opentelemetry.api.common.AttributeKey;

//TODO proper javadoc
public interface SemcovConstantsAccessor {

    //Network Attributes
    public AttributeKey<String> errorType();

    public AttributeKey<String> httpRequestMethod();

    public AttributeKey<String> accessRequestHost(); //TODO fix method name

    public AttributeKey<String> clientAddress();

    public AttributeKey<Long> httpResponseStatusCode();

    public AttributeKey<Long> localNetworkPort();

    public AttributeKey<String> networkPeerAddress();

    public AttributeKey<String> networkProtocolName();

    public AttributeKey<String> networkProtocolVersion();

    //Thread attributes

    public AttributeKey<String> threadName();

    public AttributeKey<Long> threadId();

    //Exception Attributes
    public AttributeKey<String> exceptionType();

    public AttributeKey<String> exceptionStackTrace();

    public AttributeKey<String> exceptionStackMessage();

}
