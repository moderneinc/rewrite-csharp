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

import static org.openrewrite.java.Assertions.java;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class FindClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindClass()).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @Test
    void simple() {
        rewriteRun(
          java(
            """
              package foo;
              
              class T {
              }
              """,
            """
              package foo;
              
              /*~~>*/class T {
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void nested() {
        rewriteRun(
          java(
            """
              class T {
                  class T2 {
                  }
              }
              """,
            """
              /*~~>*/class T {
                  /*~~>*/class T2 {
                  }
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void whileLoop() {
        rewriteRun(
          java(
            """
              class T {
                  public static void main(String[] args) {
                      while (true) {
                          System.out.println("OK");
                      }
                  }
              }
              """,
            """
              /*~~>*/class T {
                  public static void main(String[] args) {
                      while (true) {
                          System.out.println("OK");
                      }
                  }
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void literal() {
        rewriteRun(
          java(
            """
              class T {
                  int i = 42;
              }
              """,
            """
              /*~~>*/class T {
                  int i = 42;
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void lambda() {
        rewriteRun(
          java(
            """
              import java.util.Optional;
              
              class T {
                  int i = Optional.of(42).map(i -> i * 2).orElse(42);
              }
              """,
            """
              import java.util.Optional;
              
              /*~~>*/class T {
                  int i = Optional.of(42).map(i -> i * 2).orElse(42);
              }
              """,
            SourceSpec::noTrim
          )
        );
    }
}