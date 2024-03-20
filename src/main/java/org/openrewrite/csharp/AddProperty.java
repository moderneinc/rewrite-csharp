package org.openrewrite.csharp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.remote.JsonReceiver;
import org.openrewrite.remote.JsonSender;
import org.openrewrite.remote.ReceiverContext;
import org.openrewrite.remote.SenderContext;
import org.openrewrite.remote.properties.PropertiesReceiver;
import org.openrewrite.remote.properties.PropertiesSender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class AddProperty extends Recipe {
    @Override
    public String getDisplayName() {
        return "Adds a `from_csharp<n>` property to any given `.properties` file";
    }

    @Override
    public String getDescription() {
        return "Adds a new `from_csharp<n>` property to any given `.properties` file where `n` corresponds to the count of existing entries matching that name.\n\n" +
               "The actual recipe being executed is implemented in C# and is running in a separate process.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                try (Socket socket = new Socket("localhost", 5001)) {
                    OutputStream outputStream = socket.getOutputStream();
                    JsonSender sender = new JsonSender(outputStream);
                    PropertiesSender propertiesSender = new PropertiesSender(new SenderContext(sender));
                    propertiesSender.send(file, null);
                    sender.flush();

                    InputStream inputStream = socket.getInputStream();
                    JsonReceiver receiver = new JsonReceiver(inputStream);
                    PropertiesReceiver propertiesReceiver = new PropertiesReceiver(new ReceiverContext(receiver));
                    return (Properties.File) propertiesReceiver.receive(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Override
    public int maxCycles() {
        return 1;
    }
}
