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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.csharp.internal.CountingInputStream;
import org.openrewrite.csharp.internal.CountingOutputStream;
import org.openrewrite.marker.RecipesThatMadeChanges;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.remote.JsonReceiver;
import org.openrewrite.remote.JsonSender;
import org.openrewrite.remote.ReceiverContext;
import org.openrewrite.remote.SenderContext;
import org.openrewrite.remote.properties.PropertiesReceiver;
import org.openrewrite.remote.properties.PropertiesSender;

import java.io.IOException;
import java.net.Socket;
import java.util.Optional;

public class AddPropertyDemo extends Recipe {
    @Override
    public String getDisplayName() {
        return "Demo recipe adding a `from_csharp<n>` property to any given `.properties` file";
    }

    @Override
    public String getDescription() {
        return "Adds a new `from_csharp<n>` property to any given `.properties` file where `n` corresponds to the count of existing entries matching that name.\n\n" +
               "The actual recipe being executed is implemented in C# and is running in a separate process.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                Optional<RecipesThatMadeChanges> recipesThatMadeChanges = file.getMarkers().findFirst(RecipesThatMadeChanges.class);
                if (recipesThatMadeChanges.isPresent()) {
                    file = file.withMarkers(file.getMarkers().withMarkers(file.getMarkers().getMarkers().stream().filter(m -> m != recipesThatMadeChanges.get()).toList()));
                }
                Properties.File remoteState = ctx.getMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE");
                try (Socket socket = new Socket("localhost", 5001)) {
                    CountingOutputStream outputStream = new CountingOutputStream(socket.getOutputStream());
                    PropertiesSender sender = new PropertiesSender(new SenderContext(new JsonSender(outputStream)));
                    sender.send(file, remoteState);
                    System.out.println("\nsent " + outputStream.getCount() + " bytes\n");
                    socket.shutdownOutput();

                    CountingInputStream inputStream = new CountingInputStream(socket.getInputStream());
                    PropertiesReceiver receiver = new PropertiesReceiver(new ReceiverContext(new JsonReceiver(inputStream)));
                    remoteState = (Properties.File) receiver.receive(file);
                    System.out.println("\nreceived " + inputStream.getCount() + " bytes\n");
                    ctx.putMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE", remoteState);
                    return recipesThatMadeChanges.isPresent() ? remoteState.withMarkers(remoteState.getMarkers().add(recipesThatMadeChanges.get())) : remoteState;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Override
    public int maxCycles() {
        return 1;
    }
}
