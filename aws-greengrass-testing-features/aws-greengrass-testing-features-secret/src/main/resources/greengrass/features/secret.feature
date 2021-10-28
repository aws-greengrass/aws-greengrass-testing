Feature: Testing Secret manager in Greengrass

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario:  I can install and run aws.greengrass.SecretManager on my device
    When I create a secret named EGUAT-OTF with value password
    And I create a Greengrass deployment with components
      | aws.greengrass.secretComponent | classpath:/greengrass/components/recipes/secret.yaml |
      | aws.greengrass.SecretManager | LATEST |

    And I update secrets manager with configured secrets
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the aws.greengrass.secretComponent log on the device contains the line "\"secretString\":{\"value\":\"password\"}" within 30 seconds

