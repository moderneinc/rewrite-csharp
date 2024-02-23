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
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.scheduling.WorkingDirectoryExecutionContextView;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class DemoRecipe extends ScanningRecipe<Path> {

    @Option(displayName = "Transform",
            description = "Transform to apply.",
            example = "LambdaToAnonMethod")
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

    @Override
    public String getDisplayName() {
        return "Example WASM recipe";
    }

    @Override
    public String getDescription() {
        return "Example WASM recipe.";
    }

    @Override
    public Path getInitialValue(ExecutionContext ctx) {
        try {
            Path dir = Files.createDirectory(WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory().resolve("wasm"));
            Path wasm = dir.resolve("wasm.wasm");
            InputStream wasmStream = DemoRecipe.class.getClassLoader().getResourceAsStream("wasm.wasm");
            Files.copy(requireNonNull(wasmStream), wasm);
            return wasm;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Path acc) {
        return TreeVisitor.noop();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Path acc) {
        return Preconditions.check(new PlainTextVisitor<ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree instanceof PlainText && ((PlainText) tree).getSourcePath().getFileName().toString().endsWith(".cs") ? SearchResult.found(tree) : tree;
            }
        }, new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                try {
                    Path dir = WorkingDirectoryExecutionContextView.view(ctx).getWorkingDirectory();
                    Path sources = dir.resolve("src");
                    Path in = sources.resolve(text.getSourcePath());
                    Files.createDirectories(in.getParent());
                    Files.write(in, text.getText().getBytes(StandardCharsets.UTF_8));
                    Path out = Files.createTempFile(sources, "out", ".cs");
                    try (WasiCtx wasi = new WasiCtxBuilder()
                            .preopenedDir(sources, ".")
                            .stdout(out)
                            .inheritStderr()
                            // not sure what the purpose of the first arg is here...
                            .args(Arrays.asList("", in.toString(), transform.name()))
                            .build();
                         Store<Void> store = Store.withoutData(wasi);
                         Linker linker = new Linker(store.engine());
                         Engine engine = store.engine();
                         Module module = Module.fromFile(engine, acc.toString())) {
                        WasiCtx.addToLinker(linker);
                        linker.module(store, "whatever", module);
                        try (Func f = linker.get(store, "whatever", "_start").get().func()) {
                            f.call(store);
                            byte[] bytes = Files.readAllBytes(out);
                            return text.withText(new String(bytes, 0, bytes[bytes.length - 1] == '\n' ? bytes.length - 1 : bytes.length, StandardCharsets.UTF_8));
                        } finally {
                            if (Files.exists(out)) {
                                Files.delete(out);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }
}
