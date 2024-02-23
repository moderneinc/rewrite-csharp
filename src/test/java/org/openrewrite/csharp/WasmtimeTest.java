/*
 * Copyright 2023 the original author or authors.
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
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WasmtimeTest {

    @Test
    void wat() {
        AtomicBoolean called = new AtomicBoolean();

        try (Store<Void> store = Store.withoutData();
             Engine engine = store.engine();
             Module module = Module.fromFile(engine, "src/test/resources/hello.wat");
             Func helloFunc = WasmFunctions.wrap(store, () -> called.set(true))) {
            Collection<Extern> imports = Arrays.asList(Extern.fromFunc(helloFunc));
            try (Instance instance = new Instance(store, module, imports)) {
                try (Func f = instance.getFunc(store, "run").get()) {
                    WasmFunctions.Consumer0 fn = WasmFunctions.consumer(store, f);
                    fn.accept();
                    assertThat(called).isTrue();
                }
            }
        }
    }

    @Test
    void wasm() {
        try (WasiCtx wasi = new WasiCtxBuilder()
                .preopenedDir(Paths.get("/Users/knut/git/MyParser"), ".")
//                .stdout(Paths.get("stdout"))
                .inheritStdout()
                .inheritStderr()
                // not sure what the purpose of the first arg is here...
                .args(Arrays.asList("", "TransformVisitor.cs", "IntTypeToLongType"))
                .build();
             Store<Void> store = Store.withoutData(wasi);
             Linker linker = new Linker(store.engine());
             Engine engine = store.engine();
             Module module = Module.fromFile(engine, "/Users/knut/git/MyParser/bin/Debug/net8.0/wasi-wasm/AppBundle/MyParser.wasm")) {
            WasiCtx.addToLinker(linker);
            linker.module(store, "whatever", module);
            try (Func f = linker.get(store, "whatever", "_start").get().func()) {
                f.call(store);
            }
        }
    }
}
