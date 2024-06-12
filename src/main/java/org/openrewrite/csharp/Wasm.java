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

import io.github.kawamuray.wasmtime.*;
import io.github.kawamuray.wasmtime.Module;
import io.github.kawamuray.wasmtime.wasi.WasiCtx;
import io.github.kawamuray.wasmtime.wasi.WasiCtxBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.PackagePrivate;
import org.openrewrite.internal.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Objects.requireNonNull;

final class Wasm {

    private final String modulePath;

    @Nullable
    private WasiCtx wasi;

    private Store<?> store;
    private Linker linker;
    private Engine engine;
    private Module module;
    private Memory memory;
    private Heap heap;

    public Wasm(String modulePath) {
        this.modulePath = modulePath;
    }

    void init() {
        if (wasi == null) {
            wasi = new WasiCtxBuilder().build();
            store = Store.withoutData(wasi);
            linker = new Linker(store.engine());
            engine = store.engine();
            module = loadModule(engine);
            WasiCtx.addToLinker(linker);
            linker.module(store, "", module);
            memory = linker.get(store, "", "memory").get().memory();
            heap = Heap.create(store, memory, linker, 1_000_000);
        }
    }

    Store getStore() {
        return store;
    }

    Heap getHeap() {
        return heap;
    }

    Func func(String name) {
        return linker.get(store, "", name).get().func();
    }

    private Module loadModule(Engine engine) {
        try {
            Path devTimePath = Paths.get("src/main/resources/" + modulePath);
            if (Files.exists(devTimePath)) {
                return Module.fromFile(engine, devTimePath.toString());
            } else {
                try (InputStream in = requireNonNull(DemoRecipe.class.getClassLoader().getResourceAsStream(modulePath))) {
                    // read `in` into `byte[]`
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    in.transferTo(out);
                    return Module.fromBinary(engine, out.toByteArray());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void close() {
        if (wasi != null) {
            heap.close();
            memory.close();
            linker.close();
            engine.close();
            module.close();
            store.close();
            wasi.close();
            wasi = null;
        }
    }

    @Value
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static class Heap {
        Store<?> store;
        Memory memory;
        Linker linker;

        @PackagePrivate
        int base;

        int size;

        static Heap create(Store<?> store, Memory memory, Linker linker, int size) {
            try (Func malloc = linker.get(store, "", "malloc").get().func()) {
                Val[] result = malloc.call(store, Val.fromI32(size));
                return new Heap(store, memory, linker, result[0].i32(), size);
            }
        }

        int writeNullTerminatedString(int addr, String text) {
//            memory.grow(store, text.length());
            ByteBuffer buffer = memory.buffer(store);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            buffer.position(addr);
            buffer.put(bytes);
            buffer.put((byte) 0);
            return buffer.position();
        }

        String readString(int addr) {
            byte[] bytes = readBytesWithLength(addr);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        byte[] readBytesWithLength(int addr) {
            ByteBuffer buffer = memory.buffer(store);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int len = buffer.getInt(addr);
            byte[] bytes = new byte[len];
            buffer.position(addr + 4);
            buffer.get(bytes, 0, bytes.length);
            return bytes;
        }

        public void close() {
            try (Func free = linker.get(store, "", "free").get().func()) {
                free.call(store, Val.fromI32(base));
            }
        }
    }
}
