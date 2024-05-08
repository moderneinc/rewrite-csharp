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
import org.openrewrite.test.TypeValidation;

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
    void foreachLoop() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package io.moderne.github;
              
              import org.kohsuke.github.*;
              
              import java.io.FileWriter;
              import java.io.IOException;
              import java.time.LocalDate;
              import java.time.ZoneId;
              
              public class Main {
                  public static void main(String[] args) throws IOException {
                      String ghOrigin = args.length == 0 ? "github.com" : args[0];
                      String ghEndpoint = args.length < 2 ? "https://api.github.com" : args[1];
                      GitHub github = GitHubBuilder.fromPropertyFile()
                              .withEndpoint(ghEndpoint)
                              .build();
              
                      try (FileWriter writer = new FileWriter("repos.csv")) {
                          PagedIterator<GHOrganization> orgs = github.listOrganizations()._iterator(10);
                          while (orgs.hasNext()) {
                              GHOrganization org = orgs.next();
                              try {
                                  for (GHRepository repo : org.getRepositories().values()) {
                                      if (!repo.isArchived() && LocalDate.now().minusYears(2).isBefore(repo.getPushedAt()
                                              .toInstant()
                                              .atZone(ZoneId.systemDefault())
                                              .toLocalDate())) {
                                          writer.write(org.getLogin() + "/" + repo.getName() + "," + repo.getDefaultBranch() + ",,,,,\\n");
                                      }
                                  }
                              } catch (Throwable ignored) {
                                  // continue to the next org
                              }
                          }
                      }
                  }
              }
              """,
            """
              /*~~>*/class T {
                  void foo() {
                      for (int i : new int[] {1, 2, 3}) {
                          System.out.println(i);
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