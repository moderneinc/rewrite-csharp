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
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
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
import org.openrewrite.scheduling.WorkingDirectoryExecutionContextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

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
        State state = State.create(this, ctx);
        state.process();

        Optional<RecipesThatMadeChanges> recipesThatMadeChanges = file.getMarkers().findFirst(RecipesThatMadeChanges.class);
        if (recipesThatMadeChanges.isPresent()) {
            file = file.withMarkers(file.getMarkers().withMarkers(file.getMarkers().getMarkers().stream().filter(m -> m != recipesThatMadeChanges.get()).toList()));
        }
        Properties.File remoteState = ctx.getMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE");
        if (remoteState != null && !remoteState.equals(file)) {
            remoteState = null;
        }
        remoteState = runRecipe0(file, remoteState, state, ctx);
        ctx.putMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE", remoteState);
        return recipesThatMadeChanges.isPresent() ? remoteState.withMarkers(remoteState.getMarkers().add(recipesThatMadeChanges.get())) : remoteState;
    }

    private Properties.File runRecipe0(Properties.File file, @Nullable Properties.File remoteState, State state, ExecutionContext ctx) {
        return customViaSocket(out -> {
            PropertiesSender sender = new PropertiesSender(new SenderContext(new JsonSender(out)));
            sender.send(file, remoteState);
        }, in -> {
            PropertiesReceiver receiver = new PropertiesReceiver(new ReceiverContext(new JsonReceiver(in)));
            return (Properties.File) receiver.receive(file);
        }, state, ctx);
    }

    private Properties.File customViaSocket(Consumer<OutputStream> send, Function<InputStream, Properties.File> receive, State state, ExecutionContext ctx) {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(state.getSocket());
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
                CBORParser parser = factory.createParser(inputStream);
                parser.nextToken();
                recipes.put(this, parser.getIntValue());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        long startNanos = System.nanoTime();
        int recipeId = (int) recipes.get(this);
        try (SocketChannel socketChannel = SocketChannel.open(address)) {
            OutputStream outputStream = Channels.newOutputStream(socketChannel);
            CBORGenerator generator = factory.createGenerator(outputStream);
            generator.writeString("run-recipe");
            generator.writeNumber(recipeId);
            generator.flush();
            InputStream inputStream = Channels.newInputStream(socketChannel);
            send.accept(outputStream);
            return receive.apply(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            long nanos = System.nanoTime() - startNanos;
            System.out.println("Recipe " + recipeId + " took " + TimeUnit.NANOSECONDS.toMicros(nanos) + "us");
        }
    }

    @Override
    public int maxCycles() {
        return 1;
    }

    @Value
    @RequiredArgsConstructor
    private static class State {
        Path executable;
        Path socket;
        @NonFinal
        boolean started;

        public static State create(Recipe recipe, ExecutionContext ctx) {
            State state = ctx.getMessage(AddPropertyDemo.class.getName());
            if (state == null) {
                Path workingDirectory = WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory();
                Path path = Paths.get("/tmp/my.sock");
                if (!Files.exists(path)) {
                    path = Paths.get("/tmp").resolve(UUID.randomUUID() + ".sock");
                }
                State finalState = new State(installExecutable("demo", workingDirectory), path);
                ctx.putMessage(AddPropertyDemo.class.getName(), finalState);
//                Runtime.getRuntime().addShutdownHook(new Thread(finalState::close));
                state = finalState;
            }

            return state;
        }

        public void process() {
            if (started) {
                return;
            } else if (Files.exists(socket)) {
                UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socket);
                try (var channel = SocketChannel.open(address)) {
                    CBORGenerator generator = new CBORFactory().createGenerator(Channels.newOutputStream(channel));
                    generator.writeString("hello");
                    generator.flush();
                    started = true;
                } catch (IOException e) {
                    try {
                        Files.delete(socket);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                if (started) {
                    return;
                }
            }
            try {
                Process process = new ProcessBuilder().command(executable.toString(), socket.toString()).start();
                started = process.isAlive() || process.exitValue() == 0;
                // not working
//                cleaner.register(this, process::destroy);
                while (!Files.exists(socket)) {
                    if (!process.isAlive() || process.exitValue() != 0) {
                        started = false;
                        throw new RuntimeException("Failed to start process: " + process.exitValue());
                    }
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static Path installExecutable(String resourcePath, Path workingDirectory) {
            try {
                Path devTimePath = Paths.get("src/main/resources/" + resourcePath);
                if (Files.exists(devTimePath)) {
                    return devTimePath;
                } else {
                    try (InputStream in = requireNonNull(AddPropertyDemo.class.getClassLoader().getResourceAsStream(resourcePath))) {
                        Path targetPath = workingDirectory.resolve(resourcePath);
                        OutputStream out = Files.newOutputStream(targetPath);
                        in.transferTo(out);
                        return targetPath;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
