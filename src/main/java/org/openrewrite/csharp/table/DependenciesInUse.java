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
package org.openrewrite.csharp.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class DependenciesInUse extends DataTable<DependenciesInUse.Row> {

    public DependenciesInUse(Recipe recipe) {
        super(recipe, Row.class,
                DependenciesInUse.class.getName(),
                "Dependencies in use", "Direct and transitive dependencies in use.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Project name",
                description = "The name of the project that contains the dependency.")
        String projectName;

        @Column(displayName = "Package ID",
                description = "The NuGet package ID of the dependency.")
        String packageId;

        @Column(displayName = "Version",
                description = "The resolved package version.")
        String version;
    }
}