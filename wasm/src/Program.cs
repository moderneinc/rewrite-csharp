using System;
using System.Runtime;
using System.Runtime.InteropServices;
using System.Runtime.CompilerServices;
using wasm;

public unsafe class Program
{
    [UnmanagedCallersOnly(EntryPoint = "transform")]
    public static IntPtr transform(IntPtr source, IntPtr transform)
    {
        string sourceText = Marshal.PtrToStringUTF8(source);
        TransformKind transformKind = (TransformKind)Enum.Parse(typeof(TransformKind), Marshal.PtrToStringUTF8(transform));

        string transformed = Transforms.Transform(sourceText, transformKind);
        return Marshal.StringToHGlobalAnsi(transformed);
    }
}