Feature: Testing Cloud component in Greengrass

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @CloudDeployment @IDT @OTFStable
  Scenario: As a developer, I can create a component in Cloud and deploy it on my device
    When I create a Greengrass deployment with components
      | com.aws.HelloWorld | classpath:/greengrass/components/recipes/hello_world_recipe.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the com.aws.HelloWorld log on the device contains the line "Hello World!!" within 20 seconds
    # Deployment with new version
    When I create a Greengrass deployment with components
      | com.aws.HelloWorld | classpath:/greengrass/components/recipes/hello_world_updated_recipe.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the com.aws.HelloWorld log on the device contains the line "Hello World Updated!!" within 20 seconds

  @CloudDeployment @IDT @OTFStable
  Scenario: As a developer, I can create a component in Cloud and deploy it on my device via thing group
    When I create a Greengrass deployment with components
      | com.aws.HelloWorld | classpath:/greengrass/components/recipes/hello_world_recipe.yaml |
    And I deploy the Greengrass deployment configuration to thing group
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the com.aws.HelloWorld log on the device contains the line "Hello World!!" within 20 seconds
    # Deployment with new version
    When I create a Greengrass deployment with components
      | com.aws.HelloWorld | classpath:/greengrass/components/recipes/hello_world_updated_recipe.yaml |
    And I deploy the Greengrass deployment configuration to thing group
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the com.aws.HelloWorld log on the device contains the line "Hello World Updated!!" within 20 seconds

  @CloudDeployment @IDT @OTFStable
  Scenario: As a developer, I can create a multi-platform component in Cloud and deploy it on my device
    When I create a Greengrass deployment with components
      | com.aws.HelloWorldMultiplatform | classpath:/greengrass/components/recipes/hello_world_recipe_multiplatform.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the com.aws.HelloWorldMultiplatform log on the device contains the line "Hello World!" within 20 seconds

  @CloudDeployment @IDT @OTFStable
  Scenario: As a developer, I can create use CURRENT keyword as version of the component
    When I create a Greengrass deployment with components
      | com.aws.HelloWorld | classpath:/greengrass/components/recipes/hello_world_recipe.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the com.aws.HelloWorld log on the device contains the line "Hello World!!" within 20 seconds
    # Deployment with the same version
    When I create a Greengrass deployment with components
      | com.aws.HelloWorld | CURRENT |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
