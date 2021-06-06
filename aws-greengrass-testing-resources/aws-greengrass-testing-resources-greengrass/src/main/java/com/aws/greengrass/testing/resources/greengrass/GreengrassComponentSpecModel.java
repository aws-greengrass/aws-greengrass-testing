package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.CreateComponentVersionRequest;
import software.amazon.awssdk.services.greengrassv2.model.CreateComponentVersionResponse;
import software.amazon.awssdk.services.greengrassv2.model.LambdaFunctionRecipeSource;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface GreengrassComponentSpecModel extends ResourceSpec<GreengrassV2Client, GreengrassComponent> {
    @Nullable
    SdkBytes inlineRecipe();

    @Nullable
    LambdaFunctionRecipeSource lambdaFunction();

    @Override
    GreengrassComponent resource();

    @Override
    default GreengrassComponentSpec create(GreengrassV2Client client, AWSResources resources) {
        CreateComponentVersionResponse created = client.createComponentVersion(CreateComponentVersionRequest.builder()
                .inlineRecipe(inlineRecipe())
                .lambdaFunction(lambdaFunction())
                .build());
        return GreengrassComponentSpec.builder()
                .from(this)
                .created(true)
                .resource(GreengrassComponent.builder()
                        .componentArn(created.arn())
                        .componentName(created.componentName())
                        .componentVersion(created.componentVersion())
                        .build())
                .build();
    }
}
