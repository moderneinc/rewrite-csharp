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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;

class AddPropertyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddProperty());
    }

    @Test
    void none() {
        rewriteRun(
          properties(
            //language=properties
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
          properties(
            //language=properties
            """
              #foobar
              from_csharp0 = new_value0
              """,
            """
              #foobar
              from_csharp0 = new_value0
              from_csharp1 = new_value1
              """
          )
        );
    }
}