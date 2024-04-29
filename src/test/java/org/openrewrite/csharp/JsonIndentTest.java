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
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.json.Assertions.json;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class JsonIndentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JsonIndent()).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @Test
    void simple() {
        rewriteRun(
          json(
            """
              {
                "foo": "bar",
                "bar": ["qux"]
              }
              """,
            """
              #  {
              #    "foo": "bar",
              #    "bar": ["qux"]
              #  }
              """.replace("#", ""),
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void comment() {
        rewriteRun(
          json(
            """
              {
                //foo
                "foo": "bar",
                /*
                 * bar
                 */
                "bar": ["qux"]
              }
              """,
            """
              #  {
              #    //foo
              #    "foo": "bar",
              #    /*
              #     * bar
              #     */
              #    "bar": ["qux"]
              #  }
              """.replace("#", ""),
            SourceSpec::noTrim
          )
        );
    }
}