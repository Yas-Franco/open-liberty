package io.openliberty.microprofile.telemetry.internal.common.semconv;

import io.opentelemetry.api.common.AttributeKey;

public interface SemcovConstantsAccessor {

    public AttributeKey<String> errorType();

    public AttributeKey<String> httpRequestMethod();

    public AttributeKey<String> accessRequestHost(); //TODO fix method name

    public AttributeKey<String> clientAddress();

    public AttributeKey<Long> httpResponseStatusCode();

    public AttributeKey<Long> localNetworkPort();

    public AttributeKey<String> networkPeerAddress();
}
