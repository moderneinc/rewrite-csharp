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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.ExecutionContext;
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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

public class AddPropertyDemo extends Recipe {

    private static final Cleaner cleaner = Cleaner.create();

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

    private static Properties.File runRecipe(Properties.File file, ExecutionContext ctx) {
        Optional<RecipesThatMadeChanges> recipesThatMadeChanges = file.getMarkers().findFirst(RecipesThatMadeChanges.class);
        if (recipesThatMadeChanges.isPresent()) {
            file = file.withMarkers(file.getMarkers().withMarkers(file.getMarkers().getMarkers().stream().filter(m -> m != recipesThatMadeChanges.get()).toList()));
        }
        Properties.File remoteState = ctx.getMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE");
        remoteState = runRecipe0(file, remoteState, ctx);
        ctx.putMessage(AddPropertyDemo.class.getName() + ".REMOTE_STATE", remoteState);
        return recipesThatMadeChanges.isPresent() ? remoteState.withMarkers(remoteState.getMarkers().add(recipesThatMadeChanges.get())) : remoteState;
    }

    private static Properties.File runRecipe0(Properties.File file, @Nullable Properties.File remoteState, ExecutionContext ctx) {
//        long t0 = System.nanoTime();
//        try {
        return httpViaSocket(out -> {
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

    private static Properties.File customViaSocket(Consumer<OutputStream> send, Function<InputStream, Properties.File> receive, ExecutionContext ignore) {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(Paths.get("/tmp/mysocket"));
        try (SocketChannel socketChannel = SocketChannel.open(address)) {
            OutputStream outputStream = Channels.newOutputStream(socketChannel);
            InputStream inputStream = Channels.newInputStream(socketChannel);
            send.accept(outputStream);
            socketChannel.shutdownOutput();
            return receive.apply(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties.File httpViaSocket(Consumer<OutputStream> send, Function<InputStream, Properties.File> receive, ExecutionContext ctx) {
        try {
            DomainSocketChannel ch = getChannel(ctx);

            // Prepare HTTP POST Request
            final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.POST, "/run-recipe");

            // Add Headers
            request.headers().set(HttpHeaderNames.HOST, "localhost");
//            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
//            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

            try (ByteBufOutputStream bbos = new ByteBufOutputStream(request.content())) {
                send.accept(bbos);
                bbos.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            HttpUtil.setContentLength(request, request.content().readableBytes());

            // Make the request
            Promise<Properties.File> result = ch.eventLoop().newPromise();
            ch.writeAndFlush(request).addListener(f -> {
                if (f.isSuccess()) {
                    ch.<Consumer<FullHttpResponse>>attr(AttributeKey.valueOf("handler")).set(msg -> {
                        if (msg.status().equals(HttpResponseStatus.OK)) {
                            try (ByteBufInputStream in = new ByteBufInputStream(msg.content())) {
                                result.setSuccess(receive.apply(in));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            result.setFailure(new RuntimeException("Unexpected response: " + msg.status()));
                        }
                    });
                }
            }).sync();

            return result.sync().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static DomainSocketChannel getChannel(ExecutionContext ctx) throws InterruptedException {
        DomainSocketChannel channel = ctx.getMessage(AddPropertyDemo.class.getName() + ".CHANNEL");
        if (channel != null) {
            return channel;
        }

        DomainSocketAddress domainSocketAddress = new DomainSocketAddress("/tmp/mysocket");
        EventLoopGroup group = createEventLoopGroup();

        Bootstrap b = new Bootstrap();

        b.group(group)
                .channel(getChannelClass())
                .handler(new ChannelInitializer<>() {

                    @Override
                    public void initChannel(final Channel ch) {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(Short.MAX_VALUE))
//                                    .addLast(new LoggingHandler(LogLevel.INFO))
                                .addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                                        ctx.channel().<Consumer<FullHttpResponse>>attr(AttributeKey.valueOf("handler")).get().accept(msg);
                                    }
                                });
                    }
                });
        channel = (DomainSocketChannel) b.connect(domainSocketAddress).sync().channel();
        ctx.putMessage(AddPropertyDemo.class.getName() + ".CHANNEL", channel);
        cleaner.register(ctx, group::shutdownGracefully);

        return channel;
    }

    private static Class<? extends DomainSocketChannel> getChannelClass() {
        if (KQueue.isAvailable()) {
            return KQueueDomainSocketChannel.class;
        } else if (Epoll.isAvailable()) {
            return EpollDomainSocketChannel.class;
        } else {
            throw new IllegalStateException("Unsupported platform");
        }
    }

    private static EventLoopGroup createEventLoopGroup() {
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup();
        } else if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup();
        } else {
            throw new IllegalStateException("Unsupported platform");
        }
    }

    @Override
    public int maxCycles() {
        return 1;
    }
}
