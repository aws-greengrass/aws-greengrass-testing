/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.ProxyConfig;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.inject.Singleton;

@AutoService(Module.class)
public class RegistrationContextModule extends AbstractModule {
    private static final String ROOT_CA_URL = "https://www.amazontrust.com/repository/AmazonRootCA1.pem";

    @Provides
    @Singleton
    static RegistrationContext providesRegistrationContext(final Optional<ProxyConfig> proxy) {
        final Proxy value = proxy.map(config -> {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.host(), config.port()));
        }).orElse(Proxy.NO_PROXY);
        try (InputStream stream = new URL(ROOT_CA_URL).openConnection(value).getInputStream()) {
            return RegistrationContext.of(new String(IoUtils.toByteArray(stream), StandardCharsets.UTF_8));
        } catch (IOException ie) {
            throw new ModuleProvisionException(ie);
        }
    }
}
