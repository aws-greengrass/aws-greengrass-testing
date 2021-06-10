package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.api.util.IOUtils;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassComponentSpec;
import com.aws.greengrass.testing.resources.s3.S3BucketSpec;
import com.aws.greengrass.testing.resources.s3.S3Lifecycle;
import com.aws.greengrass.testing.resources.s3.S3ObjectSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RecipeComponentPreparationService implements ComponentPreparationService {
    private static final Logger LOGGER = LogManager.getLogger(RecipeComponentPreparationService.class);
    private static final String COMPONENT_VERSION = "ComponentVersion";
    private static final String MANIFESTS = "Manifests";
    private static final String ARTIFACTS = "Artifacts";
    private static final String URI = "URI";
    private final ContentLoader loader;
    private final ObjectMapper mapper;
    private final TestContext testContext;
    private final GreengrassContext greengrassContext;
    private final ComponentOverrides overrides;
    private final AWSResources resources;

    @FunctionalInterface
    public interface ContentLoader {
        InputStream load(String version) throws IOException;
    }

    public RecipeComponentPreparationService(
            ContentLoader loader,
            AWSResources resources,
            ObjectMapper mapper,
            TestContext testContext,
            GreengrassContext greengrassContext,
            ComponentOverrides overrides) {
        this.loader = loader;
        this.resources = resources;
        this.mapper = mapper;
        this.testContext = testContext;
        this.greengrassContext = greengrassContext;
        this.overrides = overrides;
    }

    private String uploadArtifact(String componentName, String uri, String bucketName) throws IOException {
        String[] parts = uri.split(":", 2);
        Path componentArtifact;
        // TODO: make this an extension point
        switch (parts[0]) {
            case "classpath":
                InputStream content = Objects.requireNonNull(getClass().getResourceAsStream(parts[1]),
                        "Not found on classpath: " + parts[1]);
                Path contentPath = Paths.get(parts[1]);
                componentArtifact = greengrassContext.tempDirectory()
                        .resolve(testContext.testId().id())
                        .resolve("components")
                        .resolve(componentName);
                Files.createDirectories(componentArtifact);
                componentArtifact = componentArtifact.resolve(contentPath.getFileName());
                try (FileOutputStream fos = new FileOutputStream(componentArtifact.toFile())) {
                    IOUtils.pumpStreams(content, fos);
                }
                break;
            case "file":
                componentArtifact = Paths.get(parts[1]);
                break;
            default:
                // Do nothing
                return uri;
        }
        String s3Key = parts[1];
        if (parts[1].indexOf('/') == 0) {
            s3Key = parts[1].substring(1);
        }
        resources.create(S3ObjectSpec.of(s3Key, bucketName, RequestBody.fromFile(componentArtifact)));
        LOGGER.info("Uploading {} to s3://{}/{}", componentArtifact, bucketName, s3Key);
        return "s3://" + (bucketName + "/" + s3Key);
    }

    private String getOrCreateBucket() {
        S3Lifecycle s3 = resources.lifecycle(S3Lifecycle.class);
        return Optional.ofNullable(overrides.bucketName())
                .orElseGet(() -> {
                    final String bucketName = testContext.testId().idFor("gg-component-store");
                    if (s3.bucketExists(bucketName)) {
                        return bucketName;
                    }
                    return resources.trackingSpecs(S3BucketSpec.class)
                            .filter(bucket -> bucket.bucketName().equals(bucketName))
                            .map(S3BucketSpec::bucketName)
                            .findFirst()
                            .orElseGet(resources.create(S3BucketSpec.of(bucketName))::bucketName);
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<ComponentOverrideNameVersion> prepare(ComponentOverrideNameVersion overrideNameVersion) {
        try {
            Map<String, Object> recipe = mapper.readValue(loader.load(overrideNameVersion.version().value()),
                    new TypeReference<Map<String, Object>>() {});
            recipe.compute(COMPONENT_VERSION, (key, originalValue) -> {
                return originalValue + "+" + testContext.testId().id();
            });
            List<Map<String, Object>> manifests = (List<Map<String, Object>>) recipe.get(MANIFESTS);
            for (Map<String, Object> manifest : manifests) {
                List<Map<String, Object>> artifacts = (List<Map<String, Object>>) manifest.get(ARTIFACTS);
                for (Map<String, Object> artifact : artifacts) {
                    String uri = artifact.get(URI).toString();
                    artifact.put(URI, uploadArtifact(overrideNameVersion.name(), uri, getOrCreateBucket()));
                }
            }
            GreengrassComponentSpec component = resources.create(GreengrassComponentSpec.builder()
                    .inlineRecipe(SdkBytes.fromByteArray(mapper.writeValueAsBytes(recipe)))
                    .build());
            LOGGER.info("Created component {}:{} from {}",
                    component.resource().componentName(),
                    component.resource().componentVersion(),
                    overrideNameVersion.version().value());
            return Optional.of(ComponentOverrideNameVersion.builder()
                    .from(overrideNameVersion)
                    .version(ComponentOverrideVersion.builder()
                            .from(overrideNameVersion.version())
                            .value(component.resource().componentVersion())
                            .build())
                    .build());
        } catch (NullPointerException e) {
            LOGGER.warn("Resource not found: {}", e.getMessage());
            return Optional.empty();
        } catch (IOException ie) {
            LOGGER.error("Failed to load {}:", overrideNameVersion, ie);
            return Optional.empty();
        }
    }
}
