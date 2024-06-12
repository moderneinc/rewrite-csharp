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
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.remote.RemotingClient;

import java.nio.file.Paths;
import java.util.List;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
public class JsonIndent extends Recipe {

    @Override
    public String getDisplayName() {
        return "Demo recipe indenting a JSON document using a C# recipe";
    }

    @Override
    public String getDescription() {
        return "Demo recipe indenting a JSON document using a C# recipe.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<>() {
            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                return runRecipe(document, ctx);
            }
        };
    }

    private Json.Document runRecipe(Json.Document document, ExecutionContext ctx) {
        RemotingClient remotingClient = getRemotingClient(ctx);
        return remotingClient.runRecipe(getRemoteDescriptor(), document);
    }

    private RemotingClient getRemotingClient(ExecutionContext ctx) {
        return RemotingClient.create(ctx, getClass(), Paths.get("dotnet/Rewrite.Server.dll"));
    }

    private RecipeDescriptor getRemoteDescriptor() {
        RecipeDescriptor descriptor = getDescriptor();
        List<OptionDescriptor> options = descriptor.getOptions();
        return new RecipeDescriptor("Rewrite.Json.Indent", descriptor.getDisplayName(), descriptor.getDescription(), descriptor.getTags(),
                descriptor.getEstimatedEffortPerOccurrence(), options, descriptor.getRecipeList(), descriptor.getDataTables(),
                emptyList(), emptyList(), emptyList(), descriptor.getSource());
    }

}
