package org.openrewrite.csharp;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
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
import java.util.function.Consumer;
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

    public void start() {
        if (started || (Files.exists(socket.getPath()) && (started = hello()))) {
            return;
        }
        try {
            new ProcessBuilder().command(executable.toString(), socket.toString()).start();
            long timeout = System.currentTimeMillis() + 5_000;
            while (!(started = hello()) && System.currentTimeMillis() < timeout) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hello() {
        try (var channel = SocketChannel.open(socket)) {
            CBORGenerator generator = factory.createGenerator(Channels.newOutputStream(channel));
            generator.writeString("hello");
            generator.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void reset() {
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
                throw new RuntimeException(e);
            }
        }
        return recipes.get(recipe);
    }

    public <T extends SourceFile> T runRecipe(RecipeDescriptor recipe, Consumer<OutputStream> send, Function<InputStream, T> receive) {
        int recipeId = loadRecipe(recipe);
        try (SocketChannel socketChannel = SocketChannel.open(socket)) {
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
        }

    }
}
