package com.aws.greengrass.testing.api.device.model;

import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@TestingModel
@Value.Immutable
interface CommandInputModel {
    String line();

    @Nullable
    byte[] input();

    @Nullable
    Long timeout();

    @Nullable
    List<String> args();
}
