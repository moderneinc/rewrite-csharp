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
import java.io.InputStream;

public class CountingInputStream extends InputStream {
    private final InputStream in;
    private long cnt;

    public CountingInputStream(InputStream in) {
        this.in = in;
    }

    public long getCount() {
        return this.cnt;
    }

    public int read() throws IOException {
        int val = this.in.read();
        if (val != -1) {
            ++this.cnt;
        }
        return val;
    }

    public int read(byte[] buf, int off, int len) throws IOException {
        int val = this.in.read(buf, off, len);
        if (val != -1) {
            this.cnt += (long) val;
        }
        return val;
    }

    public void close() throws IOException {
        this.in.close();
    }

    public int available() throws IOException {
        return this.in.available();
    }

    public void mark(int readlimit) {
        this.in.mark(readlimit);
    }

    public void reset() throws IOException {
        this.in.reset();
    }

    public boolean markSupported() {
        return this.in.markSupported();
    }

    public long skip(long n) throws IOException {
        long val = this.in.skip(n);
        this.cnt += val;
        return val;
    }
}
