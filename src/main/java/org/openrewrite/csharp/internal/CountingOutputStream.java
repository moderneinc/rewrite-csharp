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
package org.openrewrite.csharp.internal;

import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends OutputStream {
    private final OutputStream out;
    private long cnt;

    public CountingOutputStream(OutputStream out) {
        this.out = out;
    }

    public long getCount() {
        return this.cnt;
    }

    public void write(int val) throws IOException {
        this.out.write(val);
        ++this.cnt;
    }

    public void write(byte[] buf, int off, int len) throws IOException {
        this.out.write(buf, off, len);
        this.cnt += (long) len;
    }

    public void flush() throws IOException {
        this.out.flush();
    }

    public void close() throws IOException {
        this.out.close();
    }
}
