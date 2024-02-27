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

import io.github.kawamuray.wasmtime.Module;
import io.github.kawamuray.wasmtime.*;
import io.github.kawamuray.wasmtime.wasi.WasiCtx;
import io.github.kawamuray.wasmtime.wasi.WasiCtxBuilder;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class DemoRecipe extends Recipe {

    private static final Cleaner cleaner = Cleaner.create();
    transient WasmContext wasm = new WasmContext();

    @Option(displayName = "Transform",
            description = "Transform to apply.",
            example = "LambdaToAnonMethod",
            valid = {"LambdaToAnonMethod", "AnonMethodToLambda", "DoToWhile", "WhileToDo",
                    "CheckedStmtToUncheckedStmt", "UncheckedStmtToCheckedStmt",
                    "CheckedExprToUncheckedExpr", "UncheckedExprToCheckedExpr",
                    "PostfixToPrefix", "PrefixToPostfix", "TrueToFalse", "FalseToTrue",
                    "AddAssignToAssign", "RefParamToOutParam",
                    "OutParamToRefParam", "RefArgToOutArg", "OutArgToRefArg",
                    "OrderByAscToOrderByDesc", "OrderByDescToOrderByAsc",
                    "DefaultInitAllVars",
                    "ClassDeclToStructDecl", "StructDeclToClassDecl",
                    "IntTypeToLongType"})
    Transform transform;

    public enum Transform {
        LambdaToAnonMethod,
        AnonMethodToLambda,
        DoToWhile,
        WhileToDo,
        CheckedStmtToUncheckedStmt,
        UncheckedStmtToCheckedStmt,
        CheckedExprToUncheckedExpr,
        UncheckedExprToCheckedExpr,
        PostfixToPrefix,
        PrefixToPostfix,
        TrueToFalse,
        FalseToTrue,
        AddAssignToAssign,
        RefParamToOutParam,
        OutParamToRefParam,
        RefArgToOutArg,
        OutArgToRefArg,
        OrderByAscToOrderByDesc,
        OrderByDescToOrderByAsc,
        DefaultInitAllVars,
        ClassDeclToStructDecl,
        StructDeclToClassDecl,
        IntTypeToLongType,
    }

    public DemoRecipe(Transform transform) {
        this.transform = transform;
        cleaner.register(this, wasm::close);
    }

    @Override
    public String getDisplayName() {
        return "C# demo recipe";
    }

    @Override
    public String getDescription() {
        return "Demo recipe showcasing some simple C# transformations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new PlainTextVisitor<>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree instanceof PlainText && ((PlainText) tree).getSourcePath().getFileName().toString().endsWith(".cs") ? SearchResult.found(tree) : tree;
            }
        }, new PlainTextVisitor<>() {

            final char bomIndicator = '\uFEFF';
            final byte[] bomIndicatorBytes = "\uFEFF".getBytes(StandardCharsets.UTF_8);

            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                wasm.init();
                try (Func transform = wasm.linker.get(wasm.store, "", "transform").get().func()) {
                    int addr = wasm.heap.writeString(wasm.heap.base, text.getText());
                    int addr2 = wasm.heap.writeString(addr, DemoRecipe.this.transform.name());
                    Val[] result = transform.call(wasm.store, Val.fromI32(wasm.heap.getBase()), Val.fromI32(addr));
                    byte[] bytes = wasm.heap.readBytes(result[0].i32());
                    String oldText = text.getText();
                    boolean oldHasBom = oldText.charAt(0) == bomIndicator;
                    boolean newHasBom = true;
                    for (int i = 0; i < bomIndicatorBytes.length; i++) {
                        if (bomIndicatorBytes[i] != bytes[i]) {
                            newHasBom = false;
                            break;
                        }
                    }
                    if (oldHasBom && !newHasBom) {
                        byte[] tmp = new byte[bytes.length + bomIndicatorBytes.length];
                        System.arraycopy(bomIndicatorBytes, 0, tmp, 0, bomIndicatorBytes.length);
                        System.arraycopy(bytes, 0, tmp, bomIndicatorBytes.length, bytes.length);
                        bytes = tmp;
                    }
                    String newText = new String(
                            bytes,
                            0,
                            bytes[bytes.length - 1] == '\n' ? bytes.length - 1 : bytes.length,
                            StandardCharsets.UTF_8);
                    return text.withText(newText);
                }
            }
        });
    }

    static class WasmContext {

        @Nullable
        WasiCtx wasi;
        Store<?> store;
        Linker linker;
        Engine engine;
        Module module;
        Memory memory;
        Heap heap;

        void init() {
            if (wasi == null) {
                wasi = new WasiCtxBuilder().build();
                store = Store.withoutData(wasi);
                linker = new Linker(store.engine());
                engine = store.engine();
                module = moduleAsBytes(engine);
                WasiCtx.addToLinker(linker);
                linker.module(store, "", module);
                memory = linker.get(store, "", "memory").get().memory();
                heap = Heap.create(store, memory, linker, 1_000_000);
            }
        }

        private static Module moduleAsBytes(Engine engine) {
            try {
                Path devTimePath = Paths.get("src/main/resources/wasm.wasm");
                if (Files.exists(devTimePath)) {
                    return Module.fromFile(engine, devTimePath.toString());
                } else {
                    try (InputStream in = requireNonNull(DemoRecipe.class.getClassLoader().getResourceAsStream("wasm.wasm"))) {
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
            }
        }
    }

    @Value
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static class Heap implements AutoCloseable {
        Store<?> store;
        Memory memory;
        Linker linker;
        int base;
        int size;

        static Heap create(Store<?> store, Memory memory, Linker linker, int size) {
            try (Func malloc = linker.get(store, "", "malloc").get().func()) {
                Val[] result = malloc.call(store, Val.fromI32(size));
                return new Heap(store, memory, linker, result[0].i32(), size);
            }
        }

        int writeString(int addr, String text) {
//            memory.grow(store, text.length());
            ByteBuffer buffer = memory.buffer(store);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            buffer.position(addr);
            buffer.put(bytes);
            buffer.put((byte) 0);
            return buffer.position();
        }

        String readString(int addr) {
            byte[] bytes = readBytes(addr);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        byte[] readBytes(int addr) {
            ByteBuffer buffer = memory.buffer(store);
            int i = addr;
            for (; i < buffer.limit(); i++) {
                if (buffer.get(i) == 0) {
                    break;
                }
            }
            byte[] bytes = new byte[i - addr];
            buffer.position(addr);
            buffer.get(bytes, 0, bytes.length);
            return bytes;
        }

        @Override
        public void close() {
            try (Func free = linker.get(store, "", "free").get().func()) {
                free.call(store, Val.fromI32(base));
            }
        }
    }
}
