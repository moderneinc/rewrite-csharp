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
import org.openrewrite.csharp.table.DependenciesInUse;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.xml.Assertions.xml;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class DependencyInsightDemoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyInsightDemo());
    }

    @Test
    void none() {
        rewriteRun(
          spec -> spec.cycles(1).expectedCyclesThatMakeChanges(1)
            .dataTable(DependenciesInUse.Row.class, rows -> {
                assertThat(rows).isNotEmpty();
                assertThat(rows).allSatisfy(
                  r -> assertThat(r.getProjectFile()).isEqualTo("foo.csproj")
                );
                assertThat(rows).satisfiesExactly(
                  d -> assertThat(d.getPackageId()).isEqualTo("Microsoft.Build.Framework"),
                  d -> assertThat(d.getPackageId()).isEqualTo("Microsoft.NET.StringTools"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Collections.Immutable"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Configuration.ConfigurationManager"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Diagnostics.EventLog"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Security.Cryptography.ProtectedData"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Reflection.MetadataLoadContext"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Reflection.Metadata"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Security.Principal.Windows"),
                  d -> assertThat(d.getPackageId()).isEqualTo("System.Threading.Tasks.Dataflow")
                );
            }),
          xml(
            //language=xml
            """
              <Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                      <TargetFramework>net8.0</TargetFramework>
                  </PropertyGroup>
                  <ItemGroup>
                      <PackageReference Include="Microsoft.Build" Version="17.9.5" ExcludeAssets="runtime"/>
                      <PackageReference Include="Microsoft.Build.Locator" Version="1.7.8"/>
                      <PackageReference Include="NuGet.Protocol" Version="6.9.1" />
                  </ItemGroup>
              </Project>
              """,
            //language=xml
            """
              <!--~~>--><Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                      <TargetFramework>net8.0</TargetFramework>
                  </PropertyGroup>
                  <ItemGroup>
                      <PackageReference Include="Microsoft.Build" Version="17.9.5" ExcludeAssets="runtime"/>
                      <PackageReference Include="Microsoft.Build.Locator" Version="1.7.8"/>
                      <PackageReference Include="NuGet.Protocol" Version="6.9.1" />
                  </ItemGroup>
              </Project>
              """,
            spec -> spec.path("foo.csproj")
          )
        );
    }
}