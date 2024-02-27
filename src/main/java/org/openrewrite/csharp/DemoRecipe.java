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

import io.github.kawamuray.wasmtime.*;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.lang.ref.Cleaner;
import java.nio.charset.StandardCharsets;

@EqualsAndHashCode(callSuper = false)
@Value
public class DemoRecipe extends Recipe {

    private static final Cleaner cleaner = Cleaner.create();
    transient Wasm wasm = new Wasm("wasm.wasm");

    @Option(displayName = "Transform",
            description = "Transform to apply.",
            example = "LambdaToAnonMethod",
            valid = {"LambdaToAnonMethod", "AnonMethodToLambda", "DoToWhile", "WhileToDo",
                    "CheckedStmtToUncheckedStmt", "UncheckedStmtToCheckedStmt",
                    "CheckedExprToUncheckedExpr", "UncheckedExprToCheckedExpr",
                    "PostfixToPrefix", "PrefixToPostfix", "TrueToFalse", "FalseToTrue",
                    "AddAssignToAssign", "RefParamToOutParam",
                    "OutParamToRefParam", "RefArgToOutArg", "OutArgToRefArg",
                    "OrderByAscToOrderByDesc", "OrderByDescToOrderByAsc",
                    "DefaultInitAllVars",
                    "ClassDeclToStructDecl", "StructDeclToClassDecl",
                    "IntTypeToLongType"})
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

    public DemoRecipe(Transform transform) {
        this.transform = transform;
        cleaner.register(this, wasm::close);
    }

    @Override
    public String getDisplayName() {
        return "C# demo recipe";
    }

    @Override
    public String getDescription() {
        return "Demo recipe showcasing some simple C# transformations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new PlainTextVisitor<>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree instanceof PlainText && ((PlainText) tree).getSourcePath().getFileName().toString().endsWith(".cs") ? SearchResult.found(tree) : tree;
            }
        }, new PlainTextVisitor<>() {

            final char bomIndicator = '\uFEFF';
            final byte[] bomIndicatorBytes = "\uFEFF".getBytes(StandardCharsets.UTF_8);

            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                wasm.init();
                try (Func transform = wasm.func("transform")) {
                    wasm.getHeap().writeNullTerminatedString(wasm.getHeap().base, text.getText());
                    Val[] result = transform.call(wasm.getStore(), Val.fromI32(wasm.getHeap().getBase()), Val.fromI32(DemoRecipe.this.transform.ordinal()));
                    byte[] bytes = wasm.getHeap().readBytesWithLength(result[0].i32());
                    boolean beforeHasBom = text.getText().charAt(0) == bomIndicator;
                    if (beforeHasBom) {
                        boolean afterHasBom = true;
                        for (int i = 0; i < bomIndicatorBytes.length; i++) {
                            if (bomIndicatorBytes[i] != bytes[i]) {
                                afterHasBom = false;
                                break;
                            }
                        }
                        if (!afterHasBom) {
                            byte[] tmp = new byte[bytes.length + bomIndicatorBytes.length];
                            System.arraycopy(bomIndicatorBytes, 0, tmp, 0, bomIndicatorBytes.length);
                            System.arraycopy(bytes, 0, tmp, bomIndicatorBytes.length, bytes.length);
                            bytes = tmp;
                        }
                    }
                    String afterText = new String(bytes, StandardCharsets.UTF_8);
                    return text.withText(afterText);
                }
            }
        });
    }

}
