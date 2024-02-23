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

import com.dylibso.chicory.log.SystemLogger;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.types.Value;
import org.extism.chicory.sdk.Manifest;
import org.extism.chicory.sdk.Plugin;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.dylibso.chicory.wasm.types.ValueType.I64;

public class ExtismChicoryTest {
    public static void main(String[] args) {
        var manifest = Manifest.fromUrl("file:/Users/knut/git/MyPlugin/bin/Debug/net8.0/wasi-wasm/AppBundle/MyPlugin.wasm");

        HostFunction myHostFunction = new HostFunction(
                (Instance instance, Value... argv) -> {
                    System.out.println("Hello from a_java_func Java Function!");
                    var key = argv[0];
                    System.out.println("Reading from " + key);
                    System.out.println(instance.memory().readString((int) argv[0].asUInt(), 26));
//                    plugin.returnBytes(returns[0], "Return string from Java".getBytes());
//                    new Kernel().setInput("Return string from Java".getBytes());
                    return new Value[] {Value.i64(0)};
                },
                "extism:host/user",
                "a_java_func",
                List.of(I64),
                List.of(I64));

        var wasi = new WasiPreview1(new SystemLogger(), WasiOptions.builder().inheritSystem(args).build());
        HostFunction[] hostFunctions = wasi.toHostFunctions();
        HostFunction[] extendedHostFunctions = new HostFunction[hostFunctions.length + 1];
        System.arraycopy(hostFunctions, 0, extendedHostFunctions, 0, hostFunctions.length);
        extendedHostFunctions[hostFunctions.length] = myHostFunction;
        var plugin = new Plugin(manifest, extendedHostFunctions);

        byte[] worldBytes = "World".getBytes();
        int count = 1_000;
        for (int i = 0; i < count; i++) {
            plugin.call("greet", worldBytes);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            plugin.call("greet", worldBytes);
        }
        long t1 = System.nanoTime();
        System.out.println(TimeUnit.NANOSECONDS.toMillis(t1 - t0) + ": " + 1.0 * (t1 - t0) / count);

        System.out.println(new String(plugin.call("greet", worldBytes)));
        System.out.println(new String(plugin.call("loopback", new byte[0])));
    }
}
