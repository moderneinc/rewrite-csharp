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

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Cleaner;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddPropertyDemo extends Recipe {

    private static final Cleaner cleaner = Cleaner.create();

    @Option(displayName = "Property key",
            description = "The property key to add.",
            example = "management.metrics.enable.process.files")
    String property;

    @Option(displayName = "Property value",
            description = "The value of the new property key.")
    String value;

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
                return runRecipe(file, ctx);
            }
        };
    }

    private Properties.File runRecipe(Properties.File file, ExecutionContext ctx) {
        Optional<RecipesThatMadeChanges> recipesThatMadeChanges = file.getMarkers().findFirst(RecipesThatMadeChanges.class);
        if (recipesThatMadeChanges.isPresent()) {
            file = file.withMarkers(file.getMarkers().withMarkers(file.getMarkers().getMarkers().stream().filter(m -> m != recipesThatMadeChanges.get()).toList()));
        }
        Properties.File remoteState = ctx.getMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE");
        remoteState = runRecipe0(file, remoteState, ctx);
        ctx.putMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE", remoteState);
        return recipesThatMadeChanges.isPresent() ? remoteState.withMarkers(remoteState.getMarkers().add(recipesThatMadeChanges.get())) : remoteState;
    }

    private Properties.File runRecipe0(Properties.File file, @Nullable Properties.File remoteState, ExecutionContext ctx) {
//        long t0 = System.nanoTime();
//        try {
        return customViaSocket(out -> {
            PropertiesSender sender = new PropertiesSender(new SenderContext(new JsonSender(out)));
            sender.send(file, remoteState);
        }, in -> {
            PropertiesReceiver receiver = new PropertiesReceiver(new ReceiverContext(new JsonReceiver(in)));
            return (Properties.File) receiver.receive(file);
        }, ctx);
//        } finally {
//            long t1 = System.nanoTime();
//            System.out.println("Recipe took " + TimeUnit.NANOSECONDS.toMicros(t1 - t0) + "us");
//        }
    }

    private Properties.File customViaSocket(Consumer<OutputStream> send, Function<InputStream, Properties.File> receive, ExecutionContext ctx) {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(Paths.get("/tmp/mysocket"));
        CBORFactory factory = new CBORFactory();

        Map<Object, Object> recipes = ctx.getMessage(AddPropertyDemo.class.getName() + ".RECIPES");
        if (recipes == null) {
            recipes = new HashMap<>();
            ctx.putMessage(AddPropertyDemo.class.getName() + ".RECIPES", recipes);
            try (SocketChannel socketChannel = SocketChannel.open(address)) {
                OutputStream outputStream = Channels.newOutputStream(socketChannel);
                CBORGenerator generator = factory.createGenerator(outputStream);
                generator.writeString("reset");
                generator.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!recipes.containsKey(this)) {
            try (SocketChannel socketChannel = SocketChannel.open(address)) {
                OutputStream outputStream = Channels.newOutputStream(socketChannel);
                InputStream inputStream = Channels.newInputStream(socketChannel);
                CBORGenerator generator = factory.createGenerator(outputStream);
                generator.writeString("load-recipe");
                generator.writeString("Rewrite.Properties.AddProperty");
                generator.writeStartObject();
                generator.writeFieldName("property");
                generator.writeString(property);
                generator.writeFieldName("value");
                generator.writeString(value);
                generator.writeEndObject();
                generator.flush();
//                socketChannel.shutdownOutput();
                CBORParser parser = factory.createParser(inputStream);
                com.fasterxml.jackson.core.JsonToken token = parser.nextToken();
                recipes.put(this, parser.getIntValue());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        int recipeId = (int) recipes.get(this);
        try (SocketChannel socketChannel = SocketChannel.open(address)) {
            OutputStream outputStream = Channels.newOutputStream(socketChannel);
            CBORGenerator generator = factory.createGenerator(outputStream);
            generator.writeString("run-recipe");
            generator.writeNumber(recipeId);
            generator.flush();
            InputStream inputStream = Channels.newInputStream(socketChannel);
            send.accept(outputStream);
            socketChannel.shutdownOutput();
            return receive.apply(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int maxCycles() {
        return 1;
    }
}
