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

import org.extism.sdk.ExtismFunction;
import org.extism.sdk.HostFunction;
import org.extism.sdk.LibExtism;
import org.extism.sdk.Plugin;
import org.extism.sdk.manifest.Manifest;
import org.extism.sdk.wasm.PathWasmSource;

import java.util.List;
import java.util.Optional;

public class ExtismTest {
    public static void main(String[] args) {
        var manifest = new Manifest(List.of(new PathWasmSource(null, "/Users/knut/git/MyPlugin/bin/Debug/net8.0/wasi-wasm/AppBundle/MyPlugin.wasm", null)));
        ExtismFunction func = (plugin, params, returns, data) -> {
            System.out.println("Hello from a_java_func Java Function!");
            var key = plugin.inputString(params[0]);
            System.out.println("Reading from " + key);
            plugin.returnBytes(returns[0], "Return string from Java".getBytes());
        };
        HostFunction myHostFunction = new HostFunction<>(
                "a_java_func",
                new LibExtism.ExtismValType[]{LibExtism.ExtismValType.I64},
                new LibExtism.ExtismValType[]{LibExtism.ExtismValType.I64},
                func,
                Optional.empty()
        );
        var plugin = new Plugin(manifest, true, new HostFunction[]{myHostFunction});

//        int count = 10_000;
//        for (int i = 0; i < count; i++) {
//            plugin.call("greet", "World");
//        }
//        long t0 = System.nanoTime();
//        for (int i = 0; i < count; i++) {
//            plugin.call("greet", "World");
//        }
//        long t1 = System.nanoTime();
//        System.out.println(TimeUnit.NANOSECONDS.toMillis(t1 - t0) + ": " + 1.0 * (t1 - t0) / count);

        System.out.println(plugin.call("greet", "World"));
        System.out.println(plugin.call("loopback", (String) null));
    }
}
