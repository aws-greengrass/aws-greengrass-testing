/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import com.vdurmont.semver4j.Requirement;
import com.vdurmont.semver4j.Semver;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.greengrassv2.model.Component;
import software.amazon.awssdk.services.greengrassv2.model.ComponentLatestVersion;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVersionListItem;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVisibilityScope;

import java.util.Optional;
import javax.inject.Inject;

public class CloudComponentPreparationService implements ComponentPreparationService {
    private static final String LATEST = "LATEST";
    private final GreengrassV2Lifecycle ggv2;
    private final Region currentRegion;

    @Inject
    public CloudComponentPreparationService(final GreengrassV2Lifecycle ggv2, final Region currentRegion) {
        this.currentRegion = currentRegion;
        this.ggv2 = ggv2;
    }

    private Optional<Component> pinpointComponent(ComponentOverrideNameVersion nameVersion) {
        // We'll scan through private component override
        final Optional<Component> privateComponent = ggv2.listComponents(ComponentVisibilityScope.PRIVATE)
                .components()
                .stream()
                .filter(component -> component.componentName().equals(nameVersion.name()))
                .findFirst();
        if (privateComponent.isPresent()) {
            return privateComponent;
        }
        // We'll assume that if occlusion is not used, we'll format the arn to public components
        final Arn componentArn = Arn.builder()
                .accountId("aws")
                .partition(currentRegion.metadata().partition().id())
                .region(currentRegion.metadata().id())
                .service("greengrass")
                .resource("components:" + nameVersion.name())
                .build();
        return ggv2.latestVersionFor(componentArn.toString())
                .map(version -> Component.builder()
                        .componentName(nameVersion.name())
                        .arn(componentArn.toString())
                        .latestVersion(ComponentLatestVersion.builder()
                                .arn(version.arn())
                                .componentVersion(version.componentVersion())
                                .build())
                        .build());
    }

    private ComponentOverrideNameVersion convert(ComponentOverrideNameVersion original, String componentVersion) {
        return ComponentOverrideNameVersion.builder()
                .from(original)
                .version(ComponentOverrideVersion.builder()
                        .from(original.version())
                        .value(componentVersion)
                        .build())
                .build();
    }

    private String pinpointViableVersion(ComponentOverrideNameVersion nameVersion, Component component) {
        Requirement requirement = Requirement.buildNPM(nameVersion.version().value());
        Semver targetVersion = null;
        for (ComponentVersionListItem item : ggv2.listComponentVersions(component.arn()).componentVersions()) {
            Semver currentVersion = new Semver(item.componentVersion(), Semver.SemverType.NPM);
            if (currentVersion.satisfies(requirement)
                    && (targetVersion == null || currentVersion.isGreaterThanOrEqualTo(targetVersion))) {
                targetVersion = currentVersion;
            }
        }
        if (targetVersion == null) {
            targetVersion = new Semver(component.latestVersion().componentVersion(), Semver.SemverType.NPM);
        }
        return targetVersion.toString();
    }

    @Override
    public Optional<ComponentOverrideNameVersion> prepare(final ComponentOverrideNameVersion nameVersion) {
        return pinpointComponent(nameVersion)
                .map(component -> {
                    if (nameVersion.version().value().equals(LATEST)) {
                        return convert(nameVersion, component.latestVersion().componentVersion());
                    } else {
                        return convert(nameVersion, pinpointViableVersion(nameVersion, component));
                    }
                });
    }
}
