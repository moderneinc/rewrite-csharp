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
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

@Value
@RequiredArgsConstructor
class RemotingClient {
    Path executable;
    UnixDomainSocketAddress socket;
    CBORFactory factory = new CBORFactory();
    Map<RecipeDescriptor, Integer> recipes = new HashMap<>();
    @NonFinal
    boolean started;
    @NonFinal
    @Nullable
    SourceFile remoteState;

    public static RemotingClient create(ExecutionContext ctx) {
        RemotingClient remotingClient = ctx.getMessage(AddPropertyDemo.class.getName());
        if (remotingClient == null) {
            Path workingDirectory = WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory();
            Path path = Paths.get("/tmp/my.sock");
            if (!Files.exists(path)) {
                path = Paths.get("/tmp").resolve(UUID.randomUUID() + ".sock");
            }
            RemotingClient finalRemotingClient = new RemotingClient(installExecutable(getExecutable(), workingDirectory), UnixDomainSocketAddress.of(path));
            ctx.putMessage(AddPropertyDemo.class.getName(), finalRemotingClient);
//                Runtime.getRuntime().addShutdownHook(new Thread(finalState::close));
            remotingClient = finalRemotingClient;
        }

        return remotingClient;
    }

    private static String getExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("mac") ? "demo.osx" : "demo.linux";
    }

    private void ensureStarted() {
        if (started || (Files.exists(socket.getPath()) && (started = hello()))) {
            return;
        }
        try {
            new ProcessBuilder().command(executable.toString(), socket.toString()).start();
            long timeout = System.currentTimeMillis() + 5_000;
            while (!(started = hello()) && System.currentTimeMillis() < timeout) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hello() {
        if (!Files.exists(socket.getPath())) {
            return false;
        }
        try (var channel = SocketChannel.open(socket)) {
            CBORGenerator generator = factory.createGenerator(Channels.newOutputStream(channel));
            generator.writeString("hello");
            generator.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void reset() {
        try (SocketChannel socketChannel = SocketChannel.open(socket)) {
            OutputStream outputStream = Channels.newOutputStream(socketChannel);
            CBORGenerator generator = factory.createGenerator(outputStream);
            generator.writeString("reset");
            generator.flush();
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
                    try (OutputStream out = Files.newOutputStream(targetPath)) {
                        in.transferTo(out);
                    }
                    Files.setPosixFilePermissions(targetPath, PosixFilePermissions.fromString("rwxr-xr-x"));
                    return targetPath;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int loadRecipe(RecipeDescriptor recipe) {
        if (recipes.isEmpty()) {
            reset();
        }
        if (!recipes.containsKey(recipe)) {
            try (SocketChannel socketChannel = SocketChannel.open(socket)) {
                OutputStream outputStream = Channels.newOutputStream(socketChannel);
                InputStream inputStream = Channels.newInputStream(socketChannel);
                CBORGenerator generator = factory.createGenerator(outputStream);
                generator.writeString("load-recipe");
                generator.writeString(recipe.getName());
                generator.writeStartObject();
                List<OptionDescriptor> options = recipe.getOptions();
                for (OptionDescriptor option : options) {
                    generator.writeFieldName(option.getName());
                    generator.writeObject(option.getValue());
                }
                generator.writeEndObject();
                generator.flush();
                CBORParser parser = factory.createParser(inputStream);
                parser.nextToken();
                recipes.put(recipe, parser.getIntValue());
            } catch (IOException e) {
                throw new RuntimeException("Failed to register recipe " + recipe + " with options " + recipe.getOptions(), e);
            }
        }
        return recipes.get(recipe);
    }

    public <T extends SourceFile> T runRecipe(RecipeDescriptor recipe, T sourceFile, BiConsumer<OutputStream, T> send, Function<InputStream, T> receive) {
        ensureStarted();
        if (remoteState != null && !remoteState.equals(sourceFile)) {
            remoteState = null;
        }
        remoteState = runRecipe0(recipe, send, receive);
        return (T) remoteState;
    }

    private <T extends SourceFile> T runRecipe0(RecipeDescriptor recipe, BiConsumer<OutputStream, T> send, Function<InputStream, T> receive) {
        int recipeId = loadRecipe(recipe);
        try (SocketChannel socketChannel = SocketChannel.open(socket)) {
            OutputStream outputStream = Channels.newOutputStream(socketChannel);
            CBORGenerator generator = factory.createGenerator(outputStream);
            generator.writeString("run-recipe");
            generator.writeNumber(recipeId);
            generator.flush();
            InputStream inputStream = Channels.newInputStream(socketChannel);
            send.accept(outputStream, (T) remoteState);
            return receive.apply(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
