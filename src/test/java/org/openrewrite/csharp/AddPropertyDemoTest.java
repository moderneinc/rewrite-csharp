/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.csharp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class AddPropertyDemoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddPropertyDemo("from_csharp0", "new_value0"));
    }

    @Test
    void none() {
        rewriteRun(
          //language=properties
          properties(
            """
              #foobar
              """,
            """
              #foobar
              from_csharp0 = new_value0
              """
          )
        );
    }

    @Test
    void existing() {
        rewriteRun(
          //language=properties
          properties(
            """
              #foobar
              from_csharp0 = new_value0
              """
          )
        );
    }

    @Test
    void multiple() {
        rewriteRun(
          spec -> spec.cycles(1).expectedCyclesThatMakeChanges(1).recipeFromYaml(
            //language=yaml
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: test.recipe
              displayName: Test Recipe
              recipeList:
                - org.openrewrite.csharp.AddPropertyDemo:
                    property: from_csharp0
                    value: new_value0
                - org.openrewrite.properties.ChangePropertyValue:
                    propertyKey: from_csharp0
                    newValue: modified_by_java
                - org.openrewrite.csharp.AddPropertyDemo:
                    property: from_csharp1
                    value: new_value1
                - org.openrewrite.csharp.AddPropertyDemo:
                    property: from_csharp2
                    value: new_value2
              """, "test.recipe"
          ),
          //language=properties
          properties(
            """
              #foobar
              existing = value
              """,
            """
              #foobar
              existing = value
              from_csharp0 = modified_by_java
              from_csharp1 = new_value1
              from_csharp2 = new_value2
              """
          )
        );
    }
}