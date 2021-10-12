Feature: Testing MQTT proxying in Greengrass

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @MqttIotCore @IDT
  Scenario: Component publishes MQTT message to Iot core and retrieves it as well
    When I create a Greengrass deployment with components
      | aws.greengrass.IotMqttPublisher | classpath:/greengrass/components/recipes/iot_mqtt_subscriber.yaml |
      | aws.greengrass.IotMqttSubscriber | classpath:/greengrass/components/recipes/iot_mqtt_publisher.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    And the aws.greengrass.IotMqttSubscriber log on the device contains the line "Subscribed to IoT topic idt/Mqtt/Test with QOS=AT_LEAST_ONCE" within 20 seconds
    And the aws.greengrass.IotMqttPublisher log on the device contains the line "Published to IoT topic idt/Mqtt/Test with payload test message and qos AT_LEAST_ONCE" within 10 seconds
