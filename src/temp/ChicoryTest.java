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

import com.dylibso.chicory.log.SystemLogger;
import com.dylibso.chicory.runtime.HostImports;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Module;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;

import java.io.File;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class ChicoryTest {
    public static void main(String[] args) {
        System.getLogger("chicory");
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.ALL);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(Level.ALL);
        }
        var wasi = new WasiPreview1(new SystemLogger(), WasiOptions.builder().inheritSystem(args).build());
        var imports = new HostImports(wasi.toHostFunctions());
        long t0 = System.currentTimeMillis();
        Module module = Module.builder(new File("src/test/resources/MyFirstWasiApp.wasm")).build();
        long t1 = System.currentTimeMillis();
        System.out.println("Time to load module: " + (t1 - t0));
        Instance instance = module.instantiate(imports);
        long t2 = System.currentTimeMillis();
        System.out.println("Time to instantiate: " + (t2 - t1));
    }
}
