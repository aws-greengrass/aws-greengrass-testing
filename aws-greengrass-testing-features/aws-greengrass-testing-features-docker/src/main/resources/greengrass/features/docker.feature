Feature: Greengrass V2 Docker Component

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @Docker @IDT
  Scenario: I can deploy Docker containers as Greengrass Components
    Given the docker image amazon/amazon-ec2-metadata-mock:v1.9.0 does not exist on the device
    And I create a Greengrass deployment with components
      | DockerHubAmazonContainer | classpath:/greengrass/component/recipes/DockerHubAmazonContainer.yaml |
    When I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 2 minutes
    And I can check that the docker image amazon/amazon-ec2-metadata-mock:v1.9.0 exists on the device