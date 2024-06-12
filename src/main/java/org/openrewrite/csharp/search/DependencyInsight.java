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
package org.openrewrite.csharp.search;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.csharp.marker.ProjectDependencies;
import org.openrewrite.csharp.table.DependenciesInUse;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.remote.RemotingClient;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
public class DependencyInsight extends Recipe {

    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Option(displayName = "Package pattern",
            description = "NuGet package glob pattern used to match dependencies.",
            required = false,
            example = "System.Collections.Immutable")
    @Nullable
    String packagePattern;

    @Override
    public String getDisplayName() {
        return "C# dependency insight";
    }

    @Override
    public String getDescription() {
        return "Find all direct and transitive dependencies of a C# project with a `.csproj` file. " +
               "Results are reported in a data table.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof Xml.Document && sourceFile.getSourcePath().getFileName().toString().endsWith(".csproj");
            }

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                document = runRecipe(document, ctx);
                Optional<ProjectDependencies> dependencies = document.getMarkers().findFirst(ProjectDependencies.class);
                dependencies.ifPresent(deps -> deps.getDependencies().forEach(dep -> {
                    dependenciesInUse.insertRow(ctx, new DependenciesInUse.Row(deps.getProjectFile(), dep.get("package").toString(), dep.get("version").toString()));
                }));
                return dependencies.isPresent() ? SearchResult.found(document) : document;
            }
        };
    }

    private Xml.Document runRecipe(Xml.Document document, ExecutionContext ctx) {
        RemotingClient remotingClient = getRemotingClient(ctx);
        return remotingClient.runRecipe(getRemoteDescriptor(), document);
    }

    private RemotingClient getRemotingClient(ExecutionContext ctx) {
        return RemotingClient.create(ctx, getClass(), Paths.get("dotnet/Rewrite.Server.dll"));
    }

    private RecipeDescriptor getRemoteDescriptor() {
        RecipeDescriptor descriptor = getDescriptor();
        List<OptionDescriptor> options = descriptor.getOptions().stream().map(o -> {
            Object optionValue;
            if (o.getName().equals("packagePattern")) {
                optionValue = packagePattern;
            } else {
                optionValue = o.getValue();
            }
            return new OptionDescriptor(o.getName(), o.getType(), o.getDisplayName(), o.getDescription(), o.getExample(), o.getValid(), o.isRequired(), optionValue);
        }).toList();
        return new RecipeDescriptor("Rewrite.MSBuild.DependencyInsight", descriptor.getDisplayName(), descriptor.getDescription(), descriptor.getTags(),
                descriptor.getEstimatedEffortPerOccurrence(), options, descriptor.getRecipeList(), descriptor.getDataTables(),
                emptyList(), emptyList(), emptyList(), descriptor.getSource());
    }

}
