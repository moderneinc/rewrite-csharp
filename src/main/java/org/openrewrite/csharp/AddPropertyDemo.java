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
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.remote.JsonReceiver;
import org.openrewrite.remote.JsonSender;
import org.openrewrite.remote.ReceiverContext;
import org.openrewrite.remote.SenderContext;
import org.openrewrite.remote.properties.PropertiesReceiver;
import org.openrewrite.remote.properties.PropertiesSender;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddPropertyDemo extends Recipe {

//    private static final Cleaner cleaner = Cleaner.create();

    @Option(displayName = "Property key",
            description = "The property key to add.",
            example = "management.metrics.enable.process.files")
    String property;

    @Option(displayName = "Property value",
            description = "The value of the new property key.")
    String value;

    @Override
    public String getDisplayName() {
        return "Demo recipe adding a property to a `.properties` file using a C# recipe";
    }

    @Override
    public String getDescription() {
        return "Demonstrates how to add a property to a `.properties` file using a C# recipe.\n\n" +
               "The actual recipe being executed is implemented in C# and is running in a separate process.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                return runRecipe(file, ctx);
            }
        };
    }

    private Properties.File runRecipe(Properties.File file, ExecutionContext ctx) {
        RemotingClient remotingClient = RemotingClient.create(ctx);
        return remotingClient.runRecipe(getRemoteDescriptor(), file, (out, before) -> {
            PropertiesSender sender = new PropertiesSender(new SenderContext(new JsonSender(out)));
            sender.send(file, before);
        }, in -> {
            PropertiesReceiver receiver = new PropertiesReceiver(new ReceiverContext(new JsonReceiver(in)));
            return (Properties.File) receiver.receive(file);
        });
    }

    private RecipeDescriptor getRemoteDescriptor() {
        RecipeDescriptor descriptor = getDescriptor();
        return new RecipeDescriptor("Rewrite.Properties.AddProperty", descriptor.getDisplayName(), descriptor.getDescription(), descriptor.getTags(),
                descriptor.getEstimatedEffortPerOccurrence(), descriptor.getOptions(), descriptor.getRecipeList(), descriptor.getDataTables(),
                descriptor.getMaintainers(), descriptor.getContributors(), descriptor.getExamples(), descriptor.getSource());
    }

}
