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
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
                UnixDomainSocketAddress address = UnixDomainSocketAddress.of(Paths.get("/tmp/mysocket"));
                try (SocketChannel socketChannel = SocketChannel.open(address)) {
                    long t0 = System.nanoTime();
                    CountingOutputStream outputStream = new CountingOutputStream(Channels.newOutputStream(socketChannel));
                    PropertiesSender sender = new PropertiesSender(new SenderContext(new JsonSender(outputStream)));
                    sender.send(file, remoteState);
                    long t1 = System.nanoTime();
                    socketChannel.shutdownOutput();

                    long t2 = System.nanoTime();
                    CountingInputStream inputStream = new CountingInputStream(Channels.newInputStream(socketChannel));
                    PropertiesReceiver receiver = new PropertiesReceiver(new ReceiverContext(new JsonReceiver(inputStream)));
                    remoteState = (Properties.File) receiver.receive(file);
                    long t3 = System.nanoTime();
                    System.out.println("sent " + outputStream.getCount() + " bytes in " + TimeUnit.NANOSECONDS.toMillis(t1 - t0) + "ms / " +
                                       "received " + inputStream.getCount() + " bytes in " + TimeUnit.NANOSECONDS.toMillis(t3 - t2) + "ms");
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
