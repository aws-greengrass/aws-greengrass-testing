## Example Java Component

This is a simple Java component example demonstrating end to end feature qualification using
core framework steps.

### Layout

You'll notice the [recipe.yaml][1] at the base of the project. This is a standard Java based component
recipe template. The only difference being the URI portion:

``` yaml
Manifests:
  - Artifacts:
      - URI: "file:target/aws-greengrass-testing-examples-component-1.3.0-SNAPSHOT.jar"
```

### Integration Test Definition

To run the feature tests as part of the maven integration phase:

```xml
          <build>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
                <version>3.0.0</version>
                <executions>
                    <execution>
                        <id>feature-test</id>
                        <phase>integration-test</phase>
                        <goals>
                            <goal>run</goal>
                        </goals>
                        <configuration>
                            <target>
                                <echo message="Feature Test Suite"/>
                                <java classname="com.aws.greengrass.testing.launcher.TestLauncher"
                                      fork="true"
                                      failonerror="true"
                                      newenvironment="true"
                                      classpathref="maven.test.classpath">
                                    <!-- <sysproperty key="aws.region" value="us-east-1"/> -->
                                    <!-- <sysproperty key="gg.component.bucket" value="you-component-bucket-name" -->
                                    <sysproperty key="ggc.archive" value="/path/to/greengrass-nucleus-latest.zip"/>
                                    <sysproperty key="gg.component.overrides" value="component.Name:file:${project.basedir}/recipe.yaml"/>
                                </java>
                            </target>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
          </build>
```

Now with `mvn integration-test`, you can verify the component you are building against a released version
of Greengrass V2. This step can be executed in CI workflows and build fleets alike.

[1]: recipe.yaml