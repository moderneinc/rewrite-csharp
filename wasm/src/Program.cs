using System;
using System.Runtime;
using System.Runtime.InteropServices;
using System.Runtime.CompilerServices;
using System.Text;
using wasm;

public unsafe class Program
{
    [UnmanagedCallersOnly(EntryPoint = "transform")]
    public static IntPtr transform(IntPtr source, int transform)
    {
        string sourceText = Marshal.PtrToStringUTF8(source);
        string transformed = Transforms.Transform(sourceText, (TransformKind)transform);
        return WriteToMemory(transformed, source);
    }

    private static IntPtr WriteToMemory(string str, IntPtr destination) {
        byte[] bytes = Encoding.UTF8.GetBytes(str);
        Marshal.WriteInt32(destination, bytes.Length);
        Marshal.Copy(bytes, 0, destination + sizeof(int), bytes.Length);
        return destination;
    }
}