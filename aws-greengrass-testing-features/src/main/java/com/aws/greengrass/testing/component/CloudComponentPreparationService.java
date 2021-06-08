package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import software.amazon.awssdk.services.greengrassv2.model.Component;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVersionListItem;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVisibilityScope;

import com.vdurmont.semver4j.Semver;

import javax.inject.Inject;
import java.util.Optional;

public class CloudComponentPreparationService implements ComponentPreparationService {
    private static final String LATEST = "LATEST";
    private final GreengrassV2Lifecycle ggv2;

    @Inject
    public CloudComponentPreparationService(final GreengrassV2Lifecycle ggv2) {
        this.ggv2 = ggv2;
    }

    private Optional<Component> pinpointComponent(ComponentOverrideNameVersion nameVersion) {
        final Optional<Component> privateComponent = ggv2.listComponents(ComponentVisibilityScope.PRIVATE)
                .components()
                .stream()
                .filter(component -> component.componentName().equals(nameVersion.name()))
                .findFirst();
        if (privateComponent.isPresent()) {
            return privateComponent;
        }
        return ggv2.listComponents(ComponentVisibilityScope.PUBLIC)
                .components()
                .stream()
                .filter(component -> component.componentName().equals(nameVersion.name()))
                .findFirst();
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
        String requirement = nameVersion.version().value();
        Semver targetVersion = null;
        for (ComponentVersionListItem item : ggv2.listComponentVersions(component.arn()).componentVersions()) {
            Semver currentVersion = new Semver(item.componentVersion());
            if (currentVersion.satisfies(requirement)
                    && (targetVersion == null || currentVersion.isGreaterThanOrEqualTo(targetVersion))) {
                targetVersion = currentVersion;
            }
        }
        if (targetVersion == null) {
            targetVersion = new Semver(component.latestVersion().componentVersion());
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
