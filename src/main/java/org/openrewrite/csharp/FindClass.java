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

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.remote.RemotingClient;

import java.nio.file.Paths;
import java.util.List;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
public class FindClass extends Recipe {

    @Override
    public String getDisplayName() {
        return "Demo recipe finding Java class declarations";
    }

    @Override
    public String getDescription() {
        return "Demo recipe finding any Java class declarations using a C# recipe.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return runRecipe(cu, ctx);
            }
        };
    }

    private J.CompilationUnit runRecipe(J.CompilationUnit document, ExecutionContext ctx) {
        RemotingClient remotingClient = getRemotingClient(ctx);
        return remotingClient.runRecipe(getRemoteDescriptor(), document.withMarkers(document.getMarkers().removeByType(JavaSourceSet.class).removeByType(GitProvenance.class)));
    }

    private RemotingClient getRemotingClient(ExecutionContext ctx) {
        return RemotingClient.create(ctx, this, Paths.get("dotnet/Rewrite.Server.dll"));
    }

    private RecipeDescriptor getRemoteDescriptor() {
        RecipeDescriptor descriptor = getDescriptor();
        List<OptionDescriptor> options = descriptor.getOptions();
        return new RecipeDescriptor("Rewrite.Java.FindClass", descriptor.getDisplayName(), descriptor.getDescription(), descriptor.getTags(),
                descriptor.getEstimatedEffortPerOccurrence(), options, descriptor.getRecipeList(), descriptor.getDataTables(),
                emptyList(), emptyList(), emptyList(), descriptor.getSource());
    }

}
