package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Objects;


@Value.Immutable
@Value.Style(jdkOnly = true, visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class ProxyConfig {
    public abstract String scheme();
    public abstract String host();
    public abstract String proxyUrl();
    @Nullable
    public abstract Integer port();
    @Nullable
    public abstract String username();
    @Nullable
    public abstract String password();

    public URI toURI() {
        return URI.create(scheme() + "://" + host() + (Objects.nonNull(port()) ? ":" + port() : ""));
    }

    public static ImmutableProxyConfig.Builder builder() {
        return ImmutableProxyConfig.builder();
    }

    public static ProxyConfig fromURL(String url) {
        Integer port = null;
        String username = null, password = null;
        URI uri = URI.create(url);
        if (uri.getPort() != 0) {
            port = uri.getPort();
        }
        if (Objects.nonNull(uri.getUserInfo())) {
            String[] parts = uri.getUserInfo().split(":", 2);
            if (parts.length == 2) {
                username = parts[0];
                password = parts[1];
            }
        }
        return ProxyConfig.builder()
                .scheme(uri.getScheme())
                .host(uri.getHost())
                .port(port)
                .proxyUrl(url)
                .username(username)
                .password(password)
                .build();
    }
}
