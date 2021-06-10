package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

@TestingModel
@Value.Immutable
interface RegistrationContextModel {
    String rootCA();
}
