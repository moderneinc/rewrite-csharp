package org.openrewrite.csharp;

import io.github.kawamuray.wasmtime.Module;
import io.github.kawamuray.wasmtime.*;
import io.github.kawamuray.wasmtime.wasi.WasiCtx;
import io.github.kawamuray.wasmtime.wasi.WasiCtxBuilder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        String text = "apa";
        try (WasiCtx wasi = new WasiCtxBuilder()
          .inheritStdout()
          .inheritStderr()
          .build();
             Store<Void> store = Store.withoutData(wasi);
             Linker linker = new Linker(store.engine());
             Engine engine = store.engine();
             Module module = Module.fromFile(engine, "src/main/resources/wasm.wasm")) {
            WasiCtx.addToLinker(linker);

            linker.module(store, "", module);
            try (Memory memory = linker.get(store, "", "memory").get().memory();
                 Func malloc = linker.get(store, "", "malloc").get().func();
                 Func free = linker.get(store, "", "free").get().func();
                 Func ft = linker.get(store, "", "transform").get().func()) {

                Val[] result = malloc.call(store, Val.fromI32(1_000_000));
                Val heapBase = result[0];

                int base = result[0].i32();
                int addr2 = writeString(memory, base, "int i = 42;", store);
                int addr3 = writeString(memory, addr2, "IntTypeToLongType", store);

                long t0 = System.nanoTime();
                for (int i = 0; i < 1; i++) {
                    result = ft.call(store, heapBase, Val.fromI32(addr2));
                    text = readString(memory, result[0].i32(), store);
                }
                long t1 = System.nanoTime();
                System.out.println("time: " + TimeUnit.NANOSECONDS.toMicros((t1 - t0)));
                free.call(store, heapBase);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println(text);
        }
    }

    private static String readString(Memory memory, int addr, Store<?> store) {
        ByteBuffer buffer = memory.buffer(store);
        int i = addr;
//        buffer.position(addr);
        for (; i < buffer.limit(); i++) {
            if (buffer.get(i) == 0) {
                break;
            }
        }
        byte[] bytes = new byte[i - addr];
        buffer.position(addr);
        buffer.get(bytes, 0, bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int writeString(Memory memory, int addr, String text, Store<?> store) {
        memory.grow(store, text.length());
        ByteBuffer buffer = memory.buffer(store);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.position(addr);
        buffer.put(bytes);
        buffer.put((byte) 0);
        return buffer.position();
    }

}