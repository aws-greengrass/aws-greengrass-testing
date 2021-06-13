package com.aws.greengrass.testing.modules.model;

import com.aws.greengrass.testing.api.model.ProxyConfig;
import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;
import software.amazon.awssdk.regions.Region;

import java.util.Optional;

@TestingModel
@Value.Immutable
interface AWSResourcesContextModel {
    String envStage();
    Optional<ProxyConfig> proxyConfig();
    Region region();
}
